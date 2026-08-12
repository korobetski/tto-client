"""Copy measured NPC ratings into npcs.json.

Usage, from the repository root:

    ./gradlew :shared:desktopTest --tests '*NpcRatingBundleTest*'
    python tools/apply_npc_ratings.py
    ./gradlew :shared:desktopTest --tests '*NpcRatingBundleTest*'

The first run measures and fails (the shipped file still says the old thing),
this script writes what it measured, and the second run is green.


This script does no arithmetic
------------------------------

Every number it writes was computed by `NpcRating` in :core and handed over in
`shared/build/npc-ratings.json`.  Difficulty comes from four hundred simulated
matches against a fixed reference profile; the skill band, the payout and the
fee follow from it by formula.  All of that lives in Kotlin, next to the engine
that produced it, and is held to the shipped data by `NpcRatingBundleTest`.

Re-deriving any of it here would be a second implementation of the thing the
whole exercise exists to make single -- and a silent one, because nothing would
compare the two.  So this file reads four values per opponent and puts them
where they go.  If you want to change how hard an opponent is, change the model
or change the opponent, never this.


Both copies, and the formatting
-------------------------------

`npcs.json` is shipped twice: the client bundles it as a Compose resource and
the server reads its own copy under `resources/catalog/`.  They are byte
identical and must stay so -- the server replays what the client played, and a
roster they disagree about is a match one of them refuses.  Both are written
here, from one parse, for that reason.

Key order is preserved (`json.load` keeps insertion order) and the file is
re-emitted with the same two-space indent it was authored with, so a diff shows
the four fields that changed and nothing else.
"""

import json
import sys
from pathlib import Path

RATINGS = Path("shared/build/npc-ratings.json")

ROSTERS = (
    Path("shared/src/commonMain/composeResources/files/npcs.json"),
    Path("../tto-server/src/main/resources/catalog/npcs.json"),
)

# Exactly the fields NpcRating.rated replaces. Named here so a stray key in the
# ratings file is an error rather than a quiet extra column in the roster.
RATED_FIELDS = ("difficulty", "level", "matchFee", "MGPReward")


def key_of(entry):
    """What identifies an opponent.

    Not the icon alone: `queen-of-cards` is authored once per table and has been
    since the AS3, with a different hand and different rules each time.  Not the
    `id` either -- the FFVIII table declares `id:2` and `id:13` twice each, which
    is why NpcCatalog indexes by icon in the first place.  Icon plus the set it
    was authored for is unique, and is the pair the generator emits.
    """
    return entry["iconID"], entry["format"]


def load_ratings(path):
    if not path.exists():
        sys.exit(
            f"{path} is missing -- run the generator first:\n"
            "    ./gradlew :shared:desktopTest --tests '*NpcRatingBundleTest*'"
        )
    rows = json.loads(path.read_text())["ratings"]
    by_key = {key_of(row): row for row in rows}
    if len(by_key) != len(rows):
        sys.exit("the ratings file names an opponent twice")
    return by_key


def apply_to(path, ratings):
    roster = json.loads(path.read_text())
    npcs = roster["npcs"]

    missing = [
        npc["iconID"] for npc in npcs
        if (npc["iconID"], npc["formats"][0]) not in ratings
    ]
    if missing:
        sys.exit(f"{path}: no rating for {missing}")
    if len(ratings) != len(npcs):
        sys.exit(f"{path}: {len(npcs)} opponents but {len(ratings)} ratings")

    changed = 0
    for npc in npcs:
        rating = ratings[(npc["iconID"], npc["formats"][0])]
        before = {field: npc.get(field) for field in RATED_FIELDS}
        for field in RATED_FIELDS:
            npc[field] = rating[field]
        if before != {field: npc[field] for field in RATED_FIELDS}:
            changed += 1

    path.write_text(json.dumps(roster, indent=2, ensure_ascii=False) + "\n")
    print(f"{path}: {changed} of {len(npcs)} opponents re-rated")


def main():
    ratings = load_ratings(RATINGS)
    for roster in ROSTERS:
        if not roster.exists():
            sys.exit(f"{roster} is missing -- run this from the tto-client root")
        apply_to(roster, ratings)


if __name__ == "__main__":
    main()
