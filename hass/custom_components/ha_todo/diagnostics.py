"""What the household can attach to a bug report, with the password removed."""

from __future__ import annotations

from typing import Any

from homeassistant.components.diagnostics import async_redact_data
from homeassistant.core import HomeAssistant

from . import HaTodoConfigEntry
from .const import CONF_PASSWORD, CONF_URL

# The address goes too: it names the household's network.
REDACT = {CONF_PASSWORD, CONF_URL}


async def async_get_config_entry_diagnostics(
    hass: HomeAssistant, entry: HaTodoConfigEntry
) -> dict[str, Any]:
    view = entry.runtime_data.data
    return {
        "entry": async_redact_data(dict(entry.data), REDACT),
        "options": dict(entry.options),
        "counts": {
            "lists": len(view.lists),
            "items": sum(len(rows) for rows in view.items.values()),
            "collections": len(view.collections),
            "trackers": len(view.trackers),
            "overdue": view.overdue,
            "pending_trackers": view.pending_trackers,
        },
    }
