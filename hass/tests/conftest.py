"""Puts this directory on the path, and keeps the two halves apart.

The suite is two halves living in two environments (adr/0058, and the
``conflicts`` declaration in pyproject.toml), and each half is uncollectable in
the other's:

- ``tests/ha`` imports Home Assistant, which the pure environment does not
  have.
- ``tests/test_api.py`` serves itself over a real socket, and the Home
  Assistant harness installs ``pytest-socket``, which blocks sockets globally
  for every test in the run.

So each half is ignored where it cannot run, rather than failing there. A plain
``pytest`` then does the right thing in either environment, which is the point:
the alternative is two commands that each have to remember a path.
"""

import importlib.util
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

_HAS_HOMEASSISTANT = importlib.util.find_spec("homeassistant") is not None

if _HAS_HOMEASSISTANT:
    collect_ignore = ["test_api.py"]
else:
    collect_ignore_glob = ["ha/*"]
