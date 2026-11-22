"""The boundary adr/0058 names as its own weakness.

The ADR says the rule is written down rather than enforced, and that a
convenience ``from homeassistant.util import dt`` in ``mapping.py`` would work
perfectly and silently take the pure half across the line. This is the cheap
enforcement, so the failure is a red test rather than a discovery months later
when the harness pins a Home Assistant that has moved on.

It parses rather than imports, so it is the same answer in either environment.
"""

from __future__ import annotations

import ast
from pathlib import Path

import pytest

PKG = Path(__file__).resolve().parent.parent / "custom_components" / "ha_todo"

#: The modules that must stay framework-free.
PURE = ["api.py", "mapping.py", "models.py", "const.py"]


def _imported_roots(path: Path) -> set[str]:
    tree = ast.parse(path.read_text())
    roots: set[str] = set()
    for node in ast.walk(tree):
        if isinstance(node, ast.Import):
            roots.update(alias.name.split(".")[0] for alias in node.names)
        elif isinstance(node, ast.ImportFrom) and node.level == 0 and node.module:
            roots.add(node.module.split(".")[0])
    return roots


@pytest.mark.parametrize("module", PURE)
def test_the_pure_modules_import_no_homeassistant(module: str):
    assert "homeassistant" not in _imported_roots(PKG / module), (
        f"{module} must stay importable without Home Assistant - see adr/0058"
    )


@pytest.mark.parametrize("module", PURE)
def test_the_pure_modules_import_nothing_from_the_other_sub_projects(module: str):
    """`hass/` imports nothing from `app/`, `web/` or `e2e/` either."""
    assert not (_imported_roots(PKG / module) & {"conftest", "e2e"})


def test_every_module_that_needs_home_assistant_is_outside_that_set():
    """A sanity check on the list above, so it cannot silently go stale.

    If a new module appears in the package and is neither listed as pure nor
    importing Home Assistant, somebody has to decide which it is.
    """
    modules = {p.name for p in PKG.glob("*.py")}
    undecided = {
        name
        for name in modules - set(PURE)
        if "homeassistant" not in _imported_roots(PKG / name)
    }
    assert not undecided, (
        f"{sorted(undecided)} import no Home Assistant but are not listed as "
        "pure - add them to PURE, or they are dead code"
    )
