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
propagates from special captures only. Reverse and Fallen Ace are modelled too,
in `beats` and `effective`: Reverse swaps which side must be greater rather than
negating the test, and Fallen Ace turns a printed 10 into 0 *before* anything
else. Note Same and Plus keep reading **printed** powers — `SpecialPowerBasis`
defaults to PRINTED — so Fallen Ace does not reach them, while `touchesAceWall`
reads the effective power and therefore does.

**Not modelled: Elemental, Ascension and Descension.** All three change effective
power per cell or per tally, so a position that is "pure" here may not be under
them; those lessons have to be composed against the real engine instead.

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
FALLEN_ACE_POWER = 0
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


def effective(card, side, rules):
    """`effectivePower` with no TypeRule: Fallen Ace turns a printed 10 into 0."""
    printed = card[side]
    if rules.get("fallen_ace") and printed == ACE_POWER:
        return FALLEN_ACE_POWER
    return printed


def beats(card, side, other, rules):
    """`RulesEngine.beats` — Reverse is not a negation, it swaps which side must be greater."""
    attack = effective(card, side, rules)
    defence = effective(other, FACING[side], rules)
    return defence > attack if rules.get("reverse") else defence < attack


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
        if owner != player and beats(card, side, other, rules)
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
        if any(
            neighbour(position, s) is None and effective(card, s, rules) == ACE_POWER
            for s in SIDES
        ):
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
                    if beats(source_card, side, other, rules):
                        visited.add(at)
                        combos.append((at, "COMBO"))
                        placed[at] = (other, player)
                        following.append(at)
            frontier = following
    return placed, direct + combos


def kinds_of(captures):
    return sorted({kind for _, kind in captures})


def search(pool, active, wanted, enemies, count, cells, baselines=((),)):
    """Positions firing exactly `wanted`, and firing **nothing** under `baselines`.

    The second condition is the whole point: a placement that would have won
    anyway cannot demonstrate the rule that also applies to it.

    `baselines` is what "would have won anyway" means, and it is not always the
    empty rule set. For a lesson on one rule it is — raw power must take nothing.
    For a lesson on a *pair* of rules it cannot be: the claim there is that each
    rule alone leaves the placement dead and only the two together capture, so
    the baselines are the rules taken one at a time. See [LESSONS].

    @param cells where the taught card may be placed. Same Wall needs a wall, so
      it cannot be taught from the centre.
    """
    rules = {name: True for name in active}
    baseline_rules = [{name: True for name in names} for names in baselines]
    found = []
    for cards in permutations(pool, enemies + 1):
        played, rest = cards[0], cards[1:]
        for into in cells:
            others = [s for s in range(WIDTH * WIDTH) if s != into]
            for spots in combinations(others, enemies):
                board = {spot: (card, "RED") for spot, card in zip(spots, rest)}
                if any(resolve(board, into, played, "BLUE", b)[1] for b in baseline_rules):
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
    "same": (("same",), ["SAME"], 2, [CENTRE]),
    "plus": (("plus",), ["PLUS"], 2, [CENTRE]),
    "combo": (("same",), ["COMBO", "SAME"], 3, [CENTRE]),
    "same_wall": (("same_wall",), ["SAME_WALL"], 1, [1]),
    # Reverse and Fallen Ace both work through the *basic* comparison, so what a
    # position proves is that raw power alone captures nothing and the rule turns
    # the same placement into a capture. One enemy is enough and reads clearest.
    "reverse": (("reverse",), ["BASIC"], 1, [CENTRE]),
    "fallen_ace": (("fallen_ace",), ["BASIC"], 1, [CENTRE]),
    # The pair that makes an ace the strongest card again: Fallen Ace drops a 10
    # to 0, and under Reverse 0 is unbeatable.
    #
    # Its baselines are the two rules **one at a time**, not the empty set. An ace
    # captures plenty on raw power, so "raw power takes nothing" is unsatisfiable
    # here and would only ever return positions where the ace is irrelevant — the
    # tool's first answer before this existed was a plain Reverse capture with no
    # ace in it at all. What the lesson claims is the interaction: Reverse alone
    # makes the ace worthless, Fallen Ace alone does nothing for it, and together
    # they make it unbeatable.
    "reverse_fallen_ace": (
        ("reverse", "fallen_ace"),
        ["BASIC"],
        1,
        [CENTRE],
        (("reverse",), ("fallen_ace",)),
    ),
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
        active, wanted, enemies, cells, *rest = LESSONS[lesson]
        baselines = rest[0] if rest else ((),)
        results = search(pool, active, wanted, enemies, args.count, cells, baselines)
        print(f"\n=== {lesson} (under {', '.join(active)}) — {len(results)} position(s) ===")
        if not results:
            print("  none in this block")
        for played, into, board, captures in results:
            print(f"  play {describe(played)} at cell {into}")
            for spot, (card, owner) in sorted(board.items()):
                print(f"    cell {spot}: {describe(card)} [{owner}]")
            print(f"    -> {captures}")


if __name__ == "__main__":
    main()
