"""ha-todo as a household member.

See docs/requirements/0022 and docs/design/0022 in the repository this lives
in. The integration is a client and nothing else: every write goes through the
endpoint the web app writes through, so the per-list grant is checked by the
code that already checks it (adr/0057).
"""

from __future__ import annotations

import logging
from typing import Any

import voluptuous as vol
from homeassistant.config_entries import ConfigEntry
from homeassistant.const import Platform
from homeassistant.core import HomeAssistant, ServiceCall
from homeassistant.exceptions import (
    ConfigEntryAuthFailed,
    ConfigEntryNotReady,
    HomeAssistantError,
    ServiceValidationError,
)
from homeassistant.helpers import config_validation as cv

from .api import (
    HaTodoAuthError,
    HaTodoClient,
    HaTodoError,
    HaTodoNoSession,
)
from .const import (
    ATTR_AMOUNT,
    ATTR_COLLECTION,
    ATTR_DESCRIPTION,
    ATTR_NAME,
    ATTR_NOTE,
    ATTR_PERIOD_START,
    ATTR_TAGS,
    ATTR_TRACKER,
    CONF_PASSWORD,
    CONF_SCAN_INTERVAL,
    CONF_URL,
    CONF_USERNAME,
    CONF_VERIFY_SSL,
    DEFAULT_SCAN_INTERVAL,
    DOMAIN,
    SERVICE_ADD_COLLECTION_ENTRY,
    SERVICE_RECORD_TRACKER_READING,
)
from .coordinator import HaTodoCoordinator
from .mapping import today_in

_LOGGER = logging.getLogger(__name__)

# Deliberately no Platform.TODO: a list is a rich sensor plus explicit
# actions targeting it, not a native to-do entity. See adr/0062. The chore
# actions are registered in sensor.py as *entity* services, so their target is
# a picker rather than a list name somebody types.
PLATFORMS = [Platform.SENSOR]

type HaTodoConfigEntry = ConfigEntry[HaTodoCoordinator]

ADD_ENTRY_SCHEMA = vol.Schema(
    {
        vol.Required(ATTR_COLLECTION): cv.string,
        vol.Required(ATTR_NAME): cv.string,
        vol.Optional(ATTR_DESCRIPTION): cv.string,
        vol.Optional(ATTR_TAGS): vol.All(cv.ensure_list, [cv.string]),
    }
)

RECORD_READING_SCHEMA = vol.Schema(
    {
        vol.Required(ATTR_TRACKER): cv.string,
        vol.Required(ATTR_AMOUNT): vol.Coerce(float),
        vol.Optional(ATTR_NOTE): cv.string,
        vol.Optional(ATTR_PERIOD_START): cv.date,
    }
)


async def async_setup_entry(hass: HomeAssistant, entry: HaTodoConfigEntry) -> bool:
    client = HaTodoClient(
        entry.data[CONF_URL],
        entry.data[CONF_USERNAME],
        entry.data[CONF_PASSWORD],
        verify_ssl=entry.data.get(CONF_VERIFY_SSL, True),
    )
    try:
        await client.login()
    except (HaTodoAuthError, HaTodoNoSession) as refused:
        await client.close()
        raise ConfigEntryAuthFailed(str(refused)) from refused
    except Exception as unreachable:
        await client.close()
        raise ConfigEntryNotReady(str(unreachable)) from unreachable

    interval = int(entry.options.get(CONF_SCAN_INTERVAL, DEFAULT_SCAN_INTERVAL))
    coordinator = HaTodoCoordinator(hass, entry, client, interval)
    await coordinator.async_config_entry_first_refresh()
    entry.runtime_data = coordinator

    await hass.config_entries.async_forward_entry_setups(entry, PLATFORMS)
    entry.async_on_unload(entry.add_update_listener(_reload_on_options_change))
    _register_services(hass)
    return True


async def async_unload_entry(hass: HomeAssistant, entry: HaTodoConfigEntry) -> bool:
    unloaded = await hass.config_entries.async_unload_platforms(entry, PLATFORMS)
    if unloaded:
        await entry.runtime_data.client.close()
    return unloaded


async def _reload_on_options_change(
    hass: HomeAssistant, entry: HaTodoConfigEntry
) -> None:
    await hass.config_entries.async_reload(entry.entry_id)


def _register_services(hass: HomeAssistant) -> None:
    """The two kinds Home Assistant has no platform for (adr/0059)."""
    if hass.services.has_service(DOMAIN, SERVICE_ADD_COLLECTION_ENTRY):
        return

    def _entries(hass: HomeAssistant) -> list[HaTodoCoordinator]:
        return [
            entry.runtime_data
            for entry in hass.config_entries.async_loaded_entries(DOMAIN)
            if getattr(entry, "runtime_data", None) is not None
        ]

    def _resolve(wanted: str, candidates: list[tuple[str, str]], kind: str) -> str:
        """Finds a target by name, case-insensitively, or by its id.

        Home Assistant cannot offer a picker for something that is not an
        entity, so this is free text - and a bare "not found" against a server
        the member cannot browse from here is undebuggable. Hence the message
        naming what *is* shared.
        """
        needle = wanted.strip().casefold()
        by_id = [cid for cid, _ in candidates if cid.casefold() == needle]
        if by_id:
            return by_id[0]
        matches = [cid for cid, name in candidates if name.casefold() == needle]
        if len(matches) == 1:
            return matches[0]
        known = ", ".join(sorted(name for _, name in candidates)) or "nothing"
        if not matches:
            raise ServiceValidationError(
                f'No {kind} called "{wanted}" is shared with this account. '
                f"Shared with it: {known}."
            )
        raise ServiceValidationError(
            f'More than one {kind} is called "{wanted}". Use its id instead.'
        )

    async def add_collection_entry(call: ServiceCall) -> None:
        for coordinator in _entries(hass):
            candidates = [(c.id, c.name) for c in coordinator.data.collections]
            if not candidates:
                continue
            target = _resolve(call.data[ATTR_COLLECTION], candidates, "collection")
            try:
                await coordinator.client.add_entry(
                    target,
                    call.data[ATTR_NAME],
                    call.data.get(ATTR_DESCRIPTION),
                    call.data.get(ATTR_TAGS),
                )
            except HaTodoError as refused:
                raise HomeAssistantError(str(refused)) from refused
            await coordinator.async_request_refresh()
            return
        raise ServiceValidationError("No collection is shared with this account.")

    async def record_tracker_reading(call: ServiceCall) -> None:
        for coordinator in _entries(hass):
            candidates = [(t.id, t.name) for t in coordinator.data.trackers]
            if not candidates:
                continue
            target = _resolve(call.data[ATTR_TRACKER], candidates, "tracker")
            # Omitted means today in the *household's* zone. The server then
            # buckets it through TrackerPeriod.startOf - nothing here decides
            # which week it was.
            when = call.data.get(ATTR_PERIOD_START) or today_in(
                coordinator.client.timezone
            )
            try:
                await coordinator.client.record_reading(
                    target, call.data[ATTR_AMOUNT], when, call.data.get(ATTR_NOTE)
                )
            except HaTodoError as refused:
                raise HomeAssistantError(str(refused)) from refused
            await coordinator.async_request_refresh()
            return
        raise ServiceValidationError("No tracker is shared with this account.")

    hass.services.async_register(
        DOMAIN,
        SERVICE_ADD_COLLECTION_ENTRY,
        add_collection_entry,
        schema=ADD_ENTRY_SCHEMA,
    )
    hass.services.async_register(
        DOMAIN,
        SERVICE_RECORD_TRACKER_READING,
        record_tracker_reading,
        schema=RECORD_READING_SCHEMA,
    )
