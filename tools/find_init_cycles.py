#!/usr/bin/env python3
"""Find top-level properties that can be read before they are assigned.

Every top-level `val` in one Kotlin file becomes one JVM class initialiser, run
in declaration order. That makes two hazards, and neither is a compile error:

* **within a file** — a `val` whose initialiser names a `val` declared *below*
  it. Kotlin catches this one: "Variable 'X' must be initialized".
* **across files** — file A's initialiser reads something in file B whose
  initialiser reads back into A. Whichever class the JVM initialises second is
  already in progress when it is re-entered, so it is *not* re-run: the reader
  simply sees `null` for everything the first pass has not reached yet. The
  property is declared non-null, so nothing complains until something consumes
  it, and the stack trace names the consumer rather than the cycle:

      java.lang.NullPointerException: Parameter specified as non-null is null:
      method kotlin.collections.CollectionsKt__IterablesKt.collectionSizeOrDefault

  which is `.map` on a list that was still null. That was `TUTORIAL_COURSE`
  calling `tutorialDeck()` while `LAST_LESSON = TUTORIAL_COURSE.size - 1` was
  pulling the course in from the other file.

This reports the second kind, which the compiler cannot.

    python3 tools/find_init_cycles.py [source-root]

Exits 1 when a cycle is found, so it can be run from CI, and prints nothing but
a count when clean. Like `find_lesson_positions.py`, this needs no AS3 tree.

### What it is, and what it is not

A **textual approximation**, not a call graph. It reads each top-level `val`'s
initialiser, collects every identifier in it, and draws an edge to the file that
declares a top-level `val` or `fun` of that name. So:

* it does not follow a call two levels deep — a function that calls another
  function in a third file is invisible;
* it resolves a name to *one* declaring file, so two files declaring the same
  top-level name make it guess;
* it ignores identifiers inside `object`s and classes, whose initialisers are
  separate and lazier.

It is therefore a screen for the shape that has actually occurred, not a proof
of absence. A `val` with a `get()` is deliberately skipped: a getter is not an
initialiser and is the usual way out of a cycle once one is found.
"""

from __future__ import annotations

import collections
import pathlib
import re
import sys

VAL = re.compile(r"^(?:internal |private |public )?val ([A-Za-z_]\w*)\s*[:=]")
FUN = re.compile(r"^(?:internal |private |public )?fun ([A-Za-z_]\w*)")
NEXT_TOP_LEVEL = re.compile(r"^(?:internal |private |public )?(val|fun|object|class|@)")
COMMENT = re.compile(r"/\*.*?\*/|//[^\n]*", re.S)
WORD = re.compile(r"\b[A-Za-z_]\w*\b")


def declarations(lines: list[str]) -> tuple[dict[str, int], set[str]]:
    """The file's top-level stored `val`s, by line, and its top-level function names."""
    values, functions = {}, set()
    for line, text in enumerate(lines):
        match = VAL.match(text)
        # A `get()` is a getter, not an initialiser: it runs when read, not when the class loads.
        if match and " get()" not in text:
            values[match.group(1)] = line
        match = FUN.match(text)
        if match:
            functions.add(match.group(1))
    return values, functions


def initialiser(lines: list[str], start: int) -> str:
    """The lines belonging to the initialiser that begins at [start]."""
    body, depth = [], 0
    for line in range(start, len(lines)):
        text = lines[line]
        if line > start and depth == 0 and NEXT_TOP_LEVEL.match(text):
            break
        body.append(text)
        depth += text.count("(") + text.count("{") - text.count(")") - text.count("}")
        if line > start and depth <= 0 and text.strip() and not text.strip().startswith(("*", "//")):
            break
    return COMMENT.sub("", "\n".join(body))


def main(root: pathlib.Path) -> int:
    files = {p: p.read_text(encoding="utf-8").split("\n") for p in sorted(root.rglob("*.kt"))}
    declared = {p: declarations(lines) for p, lines in files.items()}

    owning_value, owning_function = {}, collections.defaultdict(set)
    for path, (values, functions) in declared.items():
        for name in values:
            owning_value.setdefault(name, path)
        for name in functions:
            owning_function[name].add(path)

    edges, because = collections.defaultdict(set), {}
    for path, lines in files.items():
        for name, at in declared[path][0].items():
            for token in set(WORD.findall(initialiser(lines, at))):
                if token == name:
                    continue
                target = owning_value.get(token) or next(iter(owning_function.get(token, ())), None)
                if target is not None and target != path:
                    edges[path].add(target)
                    because.setdefault((path, target), (name, token))

    cycles, seen, stack = [], set(), []

    def walk(path: pathlib.Path) -> None:
        if path in stack:
            cycles.append(stack[stack.index(path):] + [path])
            return
        if path in seen:
            return
        seen.add(path)
        stack.append(path)
        for nxt in sorted(edges[path]):
            walk(nxt)
        stack.pop()

    for path in files:
        walk(path)

    for cycle in cycles:
        print("CYCLE:")
        for start, end in zip(cycle, cycle[1:]):
            held, read = because[(start, end)]
            print(f"  {start.name}: `{held}` reads `{read}` -> {end.name}")
    print(f"cycles found: {len(cycles)}")
    return 1 if cycles else 0


if __name__ == "__main__":
    where = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else "shared/src")
    sys.exit(main(where))
