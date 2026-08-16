#!/usr/bin/env python3
"""Find board positions that provably teach one capture rule.

A tutorial lesson on Plus is only honest if the placement it asks for captures
**because of Plus** — if the same card would have won on raw power, the player
learns nothing and is told something false. This searches `cards.json` for
positions where the rule under test is the only explanation, so a lesson's board
is derived rather than guessed.

Unlike the rest of `tools/`, this needs no AS3 tree: `cards.json` is the only
input, and the output is meant to be read by a person writing a lesson, not
committed as data.

### What it is a transcription of

`RulesEngine.resolve` in `tto-core` — BASIC, SAME, PLUS, SAME_WALL and COMBO,
with the same `CAPTURE_PRECEDENCE` de-duplication and the same rule that combo
propagates from special captures only. Restricted to what a lesson uses: no
Elemental, Reverse, Fallen Ace, Ascension or Descension, so effective power is
printed power throughout and `facing`/`own` need no `SpecialPowerBasis` branch.

**This is a second implementation of rules that live in `tto-core`, and it will
drift.** It is a search tool, not an oracle: what it prints is a candidate, and
the lesson built from it must be pinned by a test that replays the position
through the real engine. See `docs/development/tutorial-and-help-proposal.md`.

Usage:

    python3 tools/find_lesson_positions.py             # the four teaching rules
    python3 tools/find_lesson_positions.py --rule plus --count 5
"""
import argparse
import json
from itertools import combinations, permutations
from pathlib import Path

CARDS_JSON = Path("shared/src/commonMain/composeResources/files/cards.json")

TOP, RIGHT, BOTTOM, LEFT = "top", "right", "bottom", "left"
SIDES = (TOP, RIGHT, BOTTOM, LEFT)
FACING = {TOP: BOTTOM, RIGHT: LEFT, BOTTOM: TOP, LEFT: RIGHT}

ACE_POWER = 10
WIDTH = 3
CENTRE = 4

# `CAPTURE_PRECEDENCE` (RulesEngine.kt) — the order a position is claimed in.
PRECEDENCE = {"PLUS": 0, "SAME": 1, "SAME_WALL": 2, "BASIC": 3, "COMBO": 4}


def neighbour(position, side):
    """The adjacent position on `side`, or None for a wall. `Board.neighbour`."""
    row, column = divmod(position, WIDTH)
    if side == TOP:
        return None if row == 0 else position - WIDTH
    if side == BOTTOM:
        return None if row == WIDTH - 1 else position + WIDTH
    if side == LEFT:
        return None if column == 0 else position - 1
    return None if column == WIDTH - 1 else position + 1


def neighbours(board, position):
    """(side, position, card, owner) for each occupied adjacent cell."""
    found = []
    for side in SIDES:
        at = neighbour(position, side)
        if at is not None and board.get(at) is not None:
            card, owner = board[at]
            found.append((side, at, card, owner))
    return found


def resolve(board, position, card, player, rules):
    """`RulesEngine.resolve` — returns (board after, [(position, kind)])."""
    placed = dict(board)
    placed[position] = (card, player)
    adjacent = neighbours(placed, position)

    basic = [
        (at, "BASIC")
        for side, at, other, owner in adjacent
        if owner != player and other[FACING[side]] < card[side]
    ]

    special = []
    if len(adjacent) >= 2 and rules.get("same"):
        matching = [n for n in adjacent if n[2][FACING[n[0]]] == card[n[0]]]
        if len(matching) >= 2:
            special += [(n[1], "SAME") for n in matching if n[3] != player]
    if len(adjacent) >= 2 and rules.get("plus"):
        sums = {}
        for side, at, other, owner in adjacent:
            sums.setdefault(other[FACING[side]] + card[side], []).append((side, at, other, owner))
        for group in sums.values():
            if len(group) >= 2:
                special += [(n[1], "PLUS") for n in group if n[3] != player]
    if rules.get("same_wall") and adjacent:
        # `touchesAceWall`: some side faces a wall and shows an ace there.
        if any(neighbour(position, s) is None and card[s] == ACE_POWER for s in SIDES):
            special += [
                (n[1], "SAME_WALL")
                for n in adjacent
                if n[2][FACING[n[0]]] == card[n[0]] and n[3] != player
            ]

    claimed, direct = set(), []
    for at, kind in sorted(special + basic, key=lambda capture: PRECEDENCE[capture[1]]):
        if at not in claimed:
            claimed.add(at)
            direct.append((at, kind))
    for at, _ in direct:
        placed[at] = (placed[at][0], player)

    combos = []
    if any(kind != "BASIC" for _, kind in direct):
        visited = {at for at, _ in direct} | {position}
        frontier = [at for at, kind in direct if kind != "BASIC"]
        while frontier:
            following = []
            for source in frontier:
                source_card = placed[source][0]
                for side, at, other, owner in neighbours(placed, source):
                    if at in visited or owner == player:
                        continue
                    if other[FACING[side]] < source_card[side]:
                        visited.add(at)
                        combos.append((at, "COMBO"))
                        placed[at] = (other, player)
                        following.append(at)
            frontier = following
    return placed, direct + combos


def kinds_of(captures):
    return sorted({kind for _, kind in captures})


def search(pool, rule, wanted, enemies, count, cells):
    """Positions firing exactly `wanted` where **raw power captures nothing**.

    The second condition is the whole point: a placement that would have won
    anyway cannot demonstrate the rule that also applies to it.

    @param cells where the taught card may be placed. Same Wall needs a wall, so
      it cannot be taught from the centre — see [LESSONS].
    """
    rules = {rule: True} if rule else {}
    found = []
    for cards in permutations(pool, enemies + 1):
        played, rest = cards[0], cards[1:]
        for into in cells:
            others = [s for s in range(WIDTH * WIDTH) if s != into]
            for spots in combinations(others, enemies):
                board = {spot: (card, "RED") for spot, card in zip(spots, rest)}
                if resolve(board, into, played, "BLUE", {})[1]:
                    continue
                captures = resolve(board, into, played, "BLUE", rules)[1]
                if kinds_of(captures) == wanted and len(captures) >= enemies:
                    found.append((played, into, board, captures))
                    if len(found) >= count:
                        return found
    return found


def describe(card):
    return (
        f"{card['name']} (id {card['id']}, "
        f"{card['top']}/{card['right']}/{card['bottom']}/{card['left']})"
    )


# lesson -> (rule to switch on, capture kinds the position must produce, enemy
# cards, cells to place into).
#
# **Combo is not a rule** — `GameRules.comboEnabled` is always true and combo fires
# whenever Same, Same Wall or Plus captures, so the combo lesson is played under
# Same and asks for a position where the chain reaches a third card.
#
# Same, Plus and Combo are taught from the centre, the cell with four neighbours
# and therefore the one where a rule has room to fire. **Same Wall is the exception
# and must not be**: it needs a side facing a wall, so the centre — the one cell
# with no wall at all — can never produce it. Taught from cell 1, a top-edge cell.
LESSONS = {
    "same": ("same", ["SAME"], 2, [CENTRE]),
    "plus": ("plus", ["PLUS"], 2, [CENTRE]),
    "combo": ("same", ["COMBO", "SAME"], 3, [CENTRE]),
    "same_wall": ("same_wall", ["SAME_WALL"], 1, [1]),
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--rule", choices=sorted(LESSONS), help="default: all of them")
    parser.add_argument("--block", type=int, default=1, help="card block to draw from")
    parser.add_argument("--count", type=int, default=2, help="positions per rule")
    args = parser.parse_args()

    catalogue = json.loads(CARDS_JSON.read_text(encoding="utf-8"))["cards"]
    pool = [card for card in catalogue if card["block"] == args.block]
    if not pool:
        raise SystemExit(f"no cards in block {args.block}")

    for lesson in [args.rule] if args.rule else sorted(LESSONS):
        rule, wanted, enemies, cells = LESSONS[lesson]
        results = search(pool, rule, wanted, enemies, args.count, cells)
        print(f"\n=== {lesson} (under {rule}) — {len(results)} position(s) ===")
        if not results:
            print("  none in this block")
        for played, into, board, captures in results:
            print(f"  play {describe(played)} at cell {into}")
            for spot, (card, owner) in sorted(board.items()):
                print(f"    cell {spot}: {describe(card)} [{owner}]")
            print(f"    -> {captures}")


if __name__ == "__main__":
    main()
