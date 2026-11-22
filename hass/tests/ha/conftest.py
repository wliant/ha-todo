"""The Home Assistant half of the suite.

A separate directory and a separate dependency group, because the harness pins
pytest, pytest-asyncio and much else exactly - see adr/0058 and the
``conflicts`` declaration in pyproject.toml. It registers its own fixtures
through an entry point, so nothing here declares ``pytest_plugins``.
"""

from __future__ import annotations

import sys
from pathlib import Path

import pytest

# Home Assistant loads the integration as `custom_components.ha_todo`, so the
# sub-project root is what goes on the path.
sys.path.insert(0, str(Path(__file__).resolve().parents[2]))


@pytest.fixture(autouse=True)
def auto_enable_custom_integrations(enable_custom_integrations):
    """Without this the harness refuses to load anything in custom_components."""
    yield
