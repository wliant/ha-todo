"""Loads the framework-free modules without Home Assistant present.

``custom_components/ha_todo/__init__.py`` imports Home Assistant, so the
package cannot be imported as a package here. The three modules adr/0058 keeps
framework-free are loaded directly instead, which is also what proves they
*are* framework-free: if one of them grows a ``homeassistant`` import, these
tests stop collecting.
"""

from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

_PKG = Path(__file__).resolve().parent.parent / "custom_components" / "ha_todo"


def _load(name: str):
    """Loads one module under a package name that satisfies its relative imports."""
    if name in sys.modules:
        return sys.modules[name]
    spec = importlib.util.spec_from_file_location(
        f"ha_todo_pure.{name}", _PKG / f"{name}.py"
    )
    assert spec and spec.loader
    module = importlib.util.module_from_spec(spec)
    sys.modules[f"ha_todo_pure.{name}"] = module
    spec.loader.exec_module(module)
    return module


# A stand-in package so `from .models import ...` inside api.py resolves.
_shim = type(sys)("ha_todo_pure")
_shim.__path__ = [str(_PKG)]
sys.modules.setdefault("ha_todo_pure", _shim)

models = _load("models")
sys.modules["ha_todo_pure.models"] = models
mapping = _load("mapping")
api = _load("api")
