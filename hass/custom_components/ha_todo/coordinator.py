"""One poll of everything shared with this account.

``local_polling``: the product is on the household's own network and this asks
it on an interval. ``/api/live/stream`` exists and would make this
``local_push``, which is the open question requirements/0022 records.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from datetime import timedelta

from homeassistant.config_entries import ConfigEntry
from homeassistant.core import HomeAssistant
from homeassistant.exceptions import ConfigEntryAuthFailed
from homeassistant.helpers.update_coordinator import DataUpdateCoordinator, UpdateFailed

from typing import Any

from .api import HaTodoAuthError, HaTodoClient, HaTodoError, HaTodoNotFound
from .const import DOMAIN
from .mapping import today_in
from .models import (
    CollectionSummary,
    Item,
    ListSummary,
    TrackerReadings,
    TrackerSummary,
)

_LOGGER = logging.getLogger(__name__)


@dataclass
class HouseholdView:
    """What one pass found. Only what has been shared with this account."""

    lists: list[ListSummary] = field(default_factory=list)
    items: dict[str, list[Item]] = field(default_factory=dict)
    fields: dict[str, dict[str, Any]] = field(default_factory=dict)
    collections: list[CollectionSummary] = field(default_factory=list)
    trackers: list[TrackerSummary] = field(default_factory=list)
    series: dict[str, TrackerReadings] = field(default_factory=dict)
    pending_trackers: list[dict[str, Any]] = field(default_factory=list)
    overdue: int = 0


class HaTodoCoordinator(DataUpdateCoordinator[HouseholdView]):
    """Asks the endpoints a member's own browser asks."""

    def __init__(
        self,
        hass: HomeAssistant,
        entry: ConfigEntry,
        client: HaTodoClient,
        interval: int,
    ) -> None:
        super().__init__(
            hass,
            _LOGGER,
            name=DOMAIN,
            update_interval=timedelta(seconds=interval),
            config_entry=entry,
        )
        self.client = client
        # One entry per list, fetched when the list is first seen. A field
        # configuration changes when its owner edits it, which is rare, so
        # re-reading it every poll would be one wasted request per list per
        # minute for the lifetime of the entry.
        self._fields: dict[str, dict[str, Any]] = {}

    async def _async_update_data(self) -> HouseholdView:
        try:
            return await self._read()
        except HaTodoAuthError as refused:
            # The client already retried a 401 once with a fresh sign-in, so
            # reaching here means the credential itself is wrong. Asking for
            # the password is the only useful response.
            raise ConfigEntryAuthFailed(str(refused)) from refused
        except HaTodoError as failure:
            raise UpdateFailed(str(failure)) from failure

    async def _read(self) -> HouseholdView:
        view = HouseholdView()
        view.lists = await self.client.lists()
        visible = {summary.id for summary in view.lists}
        # Forget lists that are no longer visible, so a revoked grant does not
        # leave its field configuration cached for the life of the entry.
        for gone in set(self._fields) - visible:
            del self._fields[gone]

        for summary in view.lists:
            try:
                if summary.id not in self._fields:
                    self._fields[summary.id] = await self.client.fields(summary.id)
                fields = self._fields[summary.id]
                view.items[summary.id] = await self.client.items(summary.id, fields)
                view.fields[summary.id] = fields
            except HaTodoNotFound:
                # The grant was revoked between two polls. Drop this list and
                # keep the rest: failing the whole refresh would take every
                # other entity unavailable with it.
                _LOGGER.debug("No longer able to read list %s", summary.id)
                self._fields.pop(summary.id, None)

        view.collections = await self.client.collections()
        view.trackers = await self.client.trackers()

        today = today_in(self.client.timezone)
        for tracker in view.trackers:
            try:
                view.series[tracker.id] = await self.client.series(
                    tracker.id, today, today, tracker.period
                )
            except HaTodoNotFound:
                _LOGGER.debug("No longer able to read tracker %s", tracker.id)

        view.pending_trackers = await self.client.pending_trackers()
        view.overdue = await self.client.overdue_total()
        return view

    def find_item(self, item_id: str) -> Item | None:
        """The cached item, which is where a write's ``version`` comes from.

        Searched across every visible list rather than within one: the services
        take an item id alone, because an id is unambiguous and asking a caller
        to also name the list is asking them to be able to get it wrong.
        """
        for items in (self.data.items.values() if self.data else ()):
            for item in items:
                if item.id == item_id:
                    return item
        return None

    def find_list(self, wanted: str) -> ListSummary | None:
        """A list by name, case-insensitively, or by id."""
        if not self.data:
            return None
        needle = wanted.strip().casefold()
        for summary in self.data.lists:
            if summary.id.casefold() == needle:
                return summary
        matches = [s for s in self.data.lists if s.name.casefold() == needle]
        return matches[0] if len(matches) == 1 else None

    def list_names(self) -> list[str]:
        return sorted(s.name for s in self.data.lists) if self.data else []
