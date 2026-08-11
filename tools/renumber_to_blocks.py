"""Renumber the shipped card data onto document 19's global ids, in place.

Usage, from the repository root:

    python tools/renumber_to_blocks.py [--check]

`--check` reports what would change and writes nothing.

## What this is, and what it is not

**A one-shot migration, not part of the build.** It reads the data this repository
already ships and rewrites it in the shape
`docs/migration/19-CARD-SETS-AND-FORMATS.md` decides:

    id = (block << 8) | number        block >= 1        number in 1..255

`extract_cards.py` emits that shape natively once it is re-run against the AS3
sources, so this exists for the data that is already here and cannot be
re-extracted without them. Delete it once the tables are replaced with authored
sets -- at which point there is nothing left to renumber.

It is idempotent by refusing rather than by guessing: every card id below 256 is
legacy and every id above is already migrated, so a second run finds nothing in
range and says so. That is the same property document 19 gives the id scheme --
"the entire range 1..255 is poison" -- used here as a safety catch.

## What it touches

- `cards.json`     -- ids, the `collection` field dropped, a `sets` block added
- `npcs.json`      -- `fetishesCards`, `cards`, and `itemRewards[].card`
- `campaigns.json` -- the same three fields on every inlined opponent
- `thumbs.json`    -- the atlas frame keys, which are texture names
- `art/ff14_62.png` -> `art/cards/013e.png`, via `git mv` so history follows

Card ids also appear **compiled into Kotlin** -- `BoosterType`'s nine pools,
`AchievementCatalog.BEAST_CARDS`, `ShopCatalog`'s card offers and
`GameSave.DEFAULT_CARDS`. Those are not rewritten here, because a script editing
Kotlin literals is worse than a person doing it: the mapping is printed at the
end instead, and `CardBundleTest` is what catches one that was missed.
"""

import argparse
import json
import subprocess
import sys
from pathlib import Path

FILES = Path("shared/src/commonMain/composeResources/files")
ART = FILES / "art"

# The two shipped tables, in the order document 19's `sets.sort_order` wants them.
# ff14 runs 1..153 and ff8 1..110, so both fit one 255-wide block with room over.
BLOCKS = {"ff14": 1, "ff8": 2}

SET_ROWS = [
    {"block": 1, "slug": "ff14", "nameKey": "APP_SET_FF14", "sortOrder": 1, "released": True},
    {"block": 2, "slug": "ff8", "nameKey": "APP_SET_FF8", "sortOrder": 2, "released": True},
]

BLOCK_SIZE = 256
MAX_NUMBER = 255


def global_id(block: int, number: int) -> int:
    """`(block << 8) | number`. The one place the id scheme is written down."""
    if block < 1:
        raise ValueError(f"block must be >= 1, was {block}")
    if not 1 <= number <= MAX_NUMBER:
        raise ValueError(f"number must be in 1..{MAX_NUMBER}, was {number}")
    return (block << 8) | number


def texture_name(card_id: int) -> str:
    """`013e` -- four lowercase hex digits, so a file sorts by set then by number."""
    return f"{card_id:04x}"


def load(name: str) -> dict:
    return json.loads((FILES / name).read_text(encoding="utf-8"))


def save(name: str, payload: dict, check: bool) -> None:
    if check:
        return
    path = FILES / name
    path.write_text(
        json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )


def renumber_cards(check: bool) -> dict:
    """Rewrites cards.json and returns `{"ff14": {1: 257, ...}, "ff8": {...}}`."""
    raw = load("cards.json")
    if "cards" in raw:
        sys.exit("cards.json is already migrated: it has a flat `cards` list")

    mapping: dict[str, dict[int, int]] = {}
    cards = []
    for slug, block in BLOCKS.items():
        table = raw[slug]
        mapping[slug] = {}
        for card in table:
            number = card["id"]
            new_id = global_id(block, number)
            mapping[slug][number] = new_id
            # `collection` goes: it was the texture prefix and the disambiguator for
            # an id that no longer needs one. `block` and `number` are derivable from
            # the id and are stored anyway, for the reason document 19's schema gives
            # -- a generated column cannot carry a foreign key.
            cards.append(
                {
                    "id": new_id,
                    "block": block,
                    "number": number,
                    "nameKey": card["nameKey"],
                    "name": card["name"],
                    "top": card["top"],
                    "right": card["right"],
                    "bottom": card["bottom"],
                    "left": card["left"],
                    "rarity": card["rarity"],
                    "type": card["type"],
                }
            )

    cards.sort(key=lambda c: c["id"])
    save("cards.json", {"sets": SET_ROWS, "cards": cards}, check)
    print(f"cards.json: {len(cards)} cards over {len(SET_ROWS)} sets")
    return mapping


def remap_npc(npc: dict, table: dict[int, int]) -> None:
    """The three places an opponent names a card, all within one table."""
    for field in ("fetishesCards", "cards"):
        npc[field] = [table[i] for i in npc[field]]
    for reward in npc.get("itemRewards", []):
        if reward.get("type") == "card":
            reward["card"] = table[reward["card"]]


def renumber_npcs(mapping: dict, check: bool) -> None:
    raw = load("npcs.json")
    for slug in BLOCKS:
        for npc in raw[slug]:
            remap_npc(npc, mapping[slug])
    save("npcs.json", raw, check)
    print(f"npcs.json: {sum(len(raw[s]) for s in BLOCKS)} opponents")


def renumber_campaigns(mapping: dict, check: bool) -> None:
    """A campaign inlines its opponents, and names the table they belong to."""
    raw = load("campaigns.json")
    for campaign in raw["campaigns"]:
        slug = campaign["collection"].rstrip("_")
        for step in campaign["steps"]:
            remap_npc(step["npc"], mapping[slug])
    save("campaigns.json", raw, check)
    print(f"campaigns.json: {len(raw['campaigns'])} campaigns")


def renumber_thumbs(mapping: dict, check: bool) -> None:
    """Atlas frame keys are texture names, so they move with the artwork."""
    raw = load("thumbs.json")
    frames = {}
    for key, frame in raw["frames"].items():
        slug, number = key.rsplit("_", 1)
        frames[texture_name(mapping[slug][int(number)])] = frame
    raw["frames"] = dict(sorted(frames.items()))
    save("thumbs.json", raw, check)
    print(f"thumbs.json: {len(frames)} frames")


def rename_art(mapping: dict, check: bool) -> None:
    """`art/ff14_62.png` -> `art/cards/013e.png`, through git so history follows."""
    moves = []
    for slug, table in mapping.items():
        for number, new_id in table.items():
            src = ART / f"{slug}_{number}.png"
            if src.exists():
                moves.append((src, ART / "cards" / f"{texture_name(new_id)}.png"))

    print(f"art: {len(moves)} card faces -> art/cards/")
    if check:
        return
    (ART / "cards").mkdir(exist_ok=True)
    for src, dest in moves:
        subprocess.run(["git", "mv", str(src), str(dest)], check=True)


def report_compiled(mapping: dict) -> None:
    """The ids that live in Kotlin, which a person moves and this only lists."""
    print("\nCompiled ff14 ids to rewrite by hand (BoosterType, BEAST_CARDS,")
    print("ShopCatalog, GameSave.DEFAULT_CARDS) -- old: new")
    table = mapping["ff14"]
    line = []
    for number in sorted(table):
        line.append(f"{number}:{table[number]}")
        if len(line) == 8:
            print("  " + "  ".join(line))
            line = []
    if line:
        print("  " + "  ".join(line))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="report, write nothing")
    args = parser.parse_args()

    if not FILES.is_dir():
        sys.exit(f"{FILES} not found -- run from the repository root")

    mapping = renumber_cards(args.check)
    renumber_npcs(mapping, args.check)
    renumber_campaigns(mapping, args.check)
    renumber_thumbs(mapping, args.check)
    rename_art(mapping, args.check)
    report_compiled(mapping)

    if args.check:
        print("\n--check: nothing written")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
