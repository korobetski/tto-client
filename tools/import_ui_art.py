#!/usr/bin/env python3
"""Copy the interface artwork out of the AS3 asset tree into the Compose resource bundle.

Companion to import_card_art.py, which handles the card faces. This one handles everything
the *screens around* a match are drawn with. Run it from the repository root:

    python tools/import_ui_art.py

Four kinds of thing, each with its own answer to the atlas question:

``avatars/``      27 FFXIV portraits at 128x128, copied individually. ``GameSave.AVATAR_ID``
                  names one of them and has since Phase 2; nothing has drawn it until now.

``npcs/``         79 opponent portraits at 50x50, copied individually and named by ``iconId``
                  — the same key ``npcs.json`` uses, so a row resolves its own portrait with
                  no second mapping to keep in step.

``icons/``        the bag and achievement icons at 40x40. The names are not chosen here: the
                  model already carries them (``Item.iconId``, ``Achievement.iconId``), and
                  this copies exactly the set those two enumerate.

``thumbs/``       the card thumbnails, kept as their three **atlases** plus a frame table.

## Why the thumbnails stay in atlases when the card faces did not

import_card_art.py argues for individual files, and it is right for what it covers: a match
shows nineteen cards out of 263, so a resident sheet would be paying 8 MB of bitmap for 1 MB
of use. The thumbnails invert every term of that.

* They are shown **all at once**. The collection browser is a grid of the whole table — that
  is what the screen is for — so there is no subset to load lazily.
* Three sheets (506x296 and two 512x512) decode to about 3 MB resident, and cover 300-odd
  thumbnails. As individual files the same set is 300 open-and-decode operations during a
  scroll.
* ``BitmapPainter`` takes a source rectangle, so a slice costs no bitmap of its own — the
  same trick ``digits.png`` already uses in CardArt.kt.

The frame table is written out as ``thumbs.json`` rather than left in the three TexturePacker
XMLs, so the app parses one small document with the JSON reader it already has instead of
carrying an XML parser for three files that never change.

Note the loose ``t (12).png`` files in the source tree are **not** used: their names carry no
usable id — ``t (12)`` is a TexturePacker export index, not a card — while the atlas XML names
every frame properly (``ff14_12_t``).
"""

from __future__ import annotations

import json
import pathlib
import re
import shutil
import sys
import xml.etree.ElementTree as ElementTree

from PIL import Image

from as3_tree import AS3_SOURCES

# tools/import_ui_art.py -> repository root
REPO = pathlib.Path(__file__).resolve().parents[1]
ASSETS = AS3_SOURCES / "assets"
RESOURCES = REPO / "shared" / "src" / "commonMain" / "composeResources" / "files"
ART = RESOURCES / "art"

# The three TexturePacker sheets, and the one name each frame is addressed by.
THUMB_ATLASES = ["ff14_card_thumbs", "ff14_card_addon_thumbs", "ff8_card_thumbs"]

# Where a frame table for one of them might be, and why there are two places.
#
# The AS3 tree carries each sheet twice: a working PNG under `assets/card_thumbs/` and the
# packed ATF the game actually loaded under `bin/assets/atlas/`, each with its own XML. **The
# two are not the same packing**, and which XML belongs to the PNG differs per sheet — the
# ff14 PNG is the 42-pixel-pitch padded layout its own XML describes, while the ff8 PNG is the
# edge-to-edge 40-pitch layout that only the *atlas* XML describes. Pairing every PNG with the
# XML beside it puts every ff8 thumbnail a fraction of a cell out.
#
# So the table is chosen by measurement rather than by folder — see `frame_table`.
THUMB_XML_DIRS = [ASSETS / "card_thumbs", AS3_SOURCES / "bin" / "assets" / "atlas"]

# Every naming the two eras use for one frame: `ff14_12_t` and `ff8_1_t` on the working sheets,
# a bare zero-padded `081_t` on the FFXIV addon one, and `ff14_thumb_12` in the atlas. `int()`
# drops the padding and a missing collection means ff14, which is the only one the addon sheet
# holds — its cards are ids 81 and up.
FRAME_NAME = re.compile(r"(?:(ff14|ff8)_)?(?:thumb_)?0*(\d+)(?:_t)?$")

# How far a frame table's own extent may sit from the sheet's opaque bounds and still be
# called its table. Two pixels is the trailing margin TexturePacker leaves; anything near the
# 40-pixel cell size is a different packing.
FRAME_FIT_TOLERANCE = 8

# Exactly the names `Item.iconId` and `Achievement.iconId` resolve to. Kept as a literal list
# rather than grepped out of the Kotlin: a missing icon should fail this script loudly, and a
# regex over source would silently pick up a renamed constant and call it a match.
ICONS = [
    # `CardItem.iconFor` — one per rarity, and the achievement tiers reuse them for difficulty.
    "card_r1_icon", "card_r2_icon", "card_r3_icon", "card_r4_icon", "card_r5_icon",
    # `BoosterItem`: the four tribe packs have their own, the five metal packs share one.
    "beast_booster", "garlean_booster", "primal_booster", "scion_booster",
    "booster_pack_icon",
    # The two consumable boosts, and the two currencies the header shows.
    "xp_boost_icon", "mgp_boost_icon", "PGS", "XP",
    # The frame the AS3 draws behind a bag item, and the achievement's own.
    "item_borders", "achievement_border",
    # `Achievements.as:17` names this one by its raw FFXIV icon id rather than descriptively —
    # it is the badge for the "defeat n NPCs" tiers. Copied under the name the model asks for.
    "000713",
]

# `PotionItem.as` names a `potionItem` texture that is in no shipped asset folder — the item
# exists in the data and its icon does not. Named here so the gap is a decision on the record
# rather than a silent fallback in the UI.
KNOWN_MISSING_ICONS = ["potionItem"]

# The same, for opponents. The Card Club's rungs and the card suits are in npcs.json and have no
# 50px portrait anywhere in the asset tree — the original listed them by name only. Enumerated so
# that this script can still fail loudly on a *twelfth*: a gap that is expected is not a gap that
# should silence the check. UiArtTest asserts the same list from the other side.
KNOWN_MISSING_PORTRAITS = [
    "club", "diamond", "dobe", "flo", "heart", "jack", "jocker",
    "ma-dincht", "piet", "spade", "trepies",
]


def copy_tree(source: pathlib.Path, target: pathlib.Path, names: list[str]) -> list[str]:
    """Copies ``<name>.png`` from *source* to *target*. Returns the names that were absent."""
    target.mkdir(parents=True, exist_ok=True)
    missing = []
    for name in names:
        origin = source / f"{name}.png"
        if origin.is_file():
            shutil.copyfile(origin, target / f"{name}.png")
        else:
            missing.append(f"{name} -> {origin}")
    return missing


def avatar_names() -> list[str]:
    """Every portrait in the avatar folder, atlas excluded — it is the same images packed."""
    return sorted(p.stem for p in (ASSETS / "avatars").glob("ffxiv_*.png"))


def npc_names() -> list[str]:
    """Every `iconID` in npcs.json, which is what a portrait has to be named to be found."""
    catalog = json.loads((RESOURCES / "npcs.json").read_text(encoding="utf-8"))
    return sorted({npc["iconID"] for group in catalog.values() for npc in group})


def frames_in(xml: pathlib.Path) -> dict[str, dict] | None:
    """The `{card key: rectangle}` this table declares, or None if it is not there."""
    if not xml.is_file():
        return None
    frames = {}
    for entry in ElementTree.parse(xml).getroot().findall("SubTexture"):
        card = FRAME_NAME.fullmatch(entry.get("name") or "")
        if card is None:
            continue
        frames[f"{card.group(1) or 'ff14'}_{int(card.group(2))}"] = {
            "x": int(entry.get("x", 0)),
            "y": int(entry.get("y", 0)),
            "width": int(entry.get("width", 0)),
            "height": int(entry.get("height", 0)),
        }
    return frames or None


def misfit(frames: dict[str, dict], sheet: pathlib.Path) -> int:
    """
    How far this table's own extent is from where the sheet's pixels actually stop.

    The whole discriminator. A table that belongs to a sheet ends where its artwork ends, to
    within TexturePacker's trailing margin; one packed for a different sheet is out by cells.
    Measured against the opaque bounding box and not the file's dimensions, because these
    sheets are padded to a power of two and 512x512 would say nothing.
    """
    with Image.open(sheet) as image:
        box = image.convert("RGBA").getbbox() or (0, 0, 0, 0)
    right = max(f["x"] + f["width"] for f in frames.values())
    bottom = max(f["y"] + f["height"] for f in frames.values())
    return abs(right - box[2]) + abs(bottom - box[3])


def frame_table(atlas: str, sheet: pathlib.Path) -> dict[str, dict] | None:
    """
    The frame table that actually describes [sheet], out of the candidates in THUMB_XML_DIRS.

    Returns None if none of them fits, which is a real failure and not a shrug: a table that
    is out by a cell renders every thumbnail as a slice of its neighbours, and that is a
    picture, so nothing downstream can notice. Better to stop here than to ship it.
    """
    scored = []
    for directory in THUMB_XML_DIRS:
        frames = frames_in(directory / f"{atlas}.xml")
        if frames is not None:
            scored.append((misfit(frames, sheet), directory.name, frames))
    if not scored:
        return None
    gap, source, frames = min(scored, key=lambda entry: entry[0])
    if gap > FRAME_FIT_TOLERANCE:
        return None
    print(f"  {atlas}: {len(frames)} frames from {source} (off by {gap}px)")
    return frames


def import_thumbs() -> tuple[int, list[str]]:
    """Copies the three sheets and writes the frame table they are addressed through."""
    out = ART / "thumbs"
    out.mkdir(parents=True, exist_ok=True)
    frames: dict[str, dict] = {}
    missing = []

    for atlas in THUMB_ATLASES:
        sheet = ASSETS / "card_thumbs" / f"{atlas}.png"
        if not sheet.is_file():
            missing.append(f"{atlas} -> {sheet}")
            continue
        table = frame_table(atlas, sheet)
        if table is None:
            missing.append(f"{atlas} -> no frame table fits this sheet")
            continue
        shutil.copyfile(sheet, out / f"{atlas}.png")
        for key, rectangle in table.items():
            frames[key] = {"sheet": atlas, **rectangle}

    (RESOURCES / "thumbs.json").write_text(
        json.dumps({"frames": frames}, indent=2, sort_keys=True) + "\n",
        encoding="utf-8",
    )
    return len(frames), missing


def main() -> int:
    missing: list[str] = []

    avatars = avatar_names()
    missing += copy_tree(ASSETS / "avatars", ART / "avatars", avatars)

    npcs = npc_names()
    absent = copy_tree(ASSETS / "npcs" / "50px" / "npcs", ART / "npcs", npcs)
    missing += [line for line in absent
                if line.split(" ->")[0] not in KNOWN_MISSING_PORTRAITS]

    missing += copy_tree(ASSETS / "misc", ART / "icons", ICONS)

    thumbs, thumb_missing = import_thumbs()
    missing += thumb_missing

    size = sum(p.stat().st_size for p in ART.rglob("*.png"))
    print(
        f"{len(avatars)} avatars, {len(npcs)} portraits, {len(ICONS)} icons, "
        f"{thumbs} thumbnails in {len(THUMB_ATLASES)} sheets"
    )
    print(f"art/ is now {size / 1048576:.2f} MB")
    known = KNOWN_MISSING_ICONS + KNOWN_MISSING_PORTRAITS
    print(f"not shipped, and known to be: {', '.join(known)}")

    if missing:
        print(f"\n{len(missing)} MISSING:", file=sys.stderr)
        for line in missing:
            print(f"  {line}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
