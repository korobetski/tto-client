"""Where the AS3 original's tree is, now that it is not in this repository.

Every script beside this one reads out of the Adobe AIR original — `.as` tables, artwork,
sounds, locale bundles — and writes into `shared/src/commonMain/composeResources/` or
`androidApp/src/main/res/`. Their *output* is committed here; their *input* is not. The AS3
tree lives in the `AS3-Triple-Triad` repository, under `sources/`, and this client repository
was split off from it deliberately without carrying that tree along.

So the scripts need to be told where it is. In order:

1. `TTO_AS3_SOURCES`, an absolute path to the `sources/` directory of an
   `AS3-Triple-Triad` checkout — for a checkout that is not beside this one;
2. otherwise `../AS3-Triple-Triad/sources`, relative to this repository, which is the
   layout the sibling repositories (`tto-core`, `tto-server`) already assume.

Nothing here fails on import: a missing tree is only an error for a script that is actually
run, and every one of them already reports its own missing input. `require()` is there for
the ones that want to say so before doing any work.
"""

from __future__ import annotations

import os
import pathlib
import sys

REPO = pathlib.Path(__file__).resolve().parents[1]

AS3_SOURCES = (
    pathlib.Path(os.environ["TTO_AS3_SOURCES"]).expanduser().resolve()
    if os.environ.get("TTO_AS3_SOURCES")
    else REPO.parent / "AS3-Triple-Triad" / "sources"
)


def require() -> pathlib.Path:
    """Return the AS3 tree, or exit 1 explaining how to point at it."""
    if not AS3_SOURCES.is_dir():
        print(
            f"no AS3 source tree at {AS3_SOURCES}\n"
            "clone https://github.com/korobetski/AS3-Triple-Triad beside this repository, "
            "or set TTO_AS3_SOURCES to its sources/ directory",
            file=sys.stderr,
        )
        raise SystemExit(1)
    return AS3_SOURCES
