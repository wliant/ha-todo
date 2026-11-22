"""What a tracker currently reads, and two counts worth automating against."""

from __future__ import annotations

import logging
from decimal import Decimal

from homeassistant.components.sensor import (
    SensorEntity,
    SensorStateClass,
)
from homeassistant.core import HomeAssistant, ServiceResponse, SupportsResponse
from homeassistant.exceptions import HomeAssistantError, ServiceValidationError
from homeassistant.helpers import config_validation as cv, entity_platform
from homeassistant.helpers.device_registry import DeviceInfo
from homeassistant.helpers.entity_platform import AddConfigEntryEntitiesCallback
from homeassistant.helpers.update_coordinator import CoordinatorEntity

from enum import IntFlag
from typing import Any

import voluptuous as vol

from . import HaTodoConfigEntry
from .api import HaTodoError, HaTodoStale
from .const import (
    ATTR_DESCRIPTION,
    ATTR_DUE_DATE,
    ATTR_DUE_DATETIME,
    ATTR_ITEM,
    DOMAIN,
    MAX_PUBLISHED_ITEMS,
    SERVICE_ADD_ITEM,
    SERVICE_COMPLETE_ITEM,
    SERVICE_LAPSE_ITEM,
    SERVICE_REOPEN_ITEM,
)
from .coordinator import HaTodoCoordinator
from .mapping import (
    STATE_COMPLETED,
    STATE_LAPSED,
    STATE_OPEN,
    is_overdue,
    to_instant,
)
from .models import ListSummary, TrackerSummary

SCOPE_SHARED = "SHARED"

_LOGGER = logging.getLogger(__name__)


class ListFeature(IntFlag):
    """What a sensor of ours can be asked to do.

    Entity services are shared by every platform in a domain, so registering
    these on `sensor` offers them to the tracker and the two count sensors as
    well. ``required_features`` is what refuses those cleanly - "does not
    support this service" - instead of an AttributeError from deep inside the
    service call.
    """

    ITEMS = 1


ADD_ITEM_FIELDS = {
    vol.Required(ATTR_DESCRIPTION): cv.string,
    # A bare date means the end of that day in the household's zone; a datetime
    # means what it says. Both, because "tomorrow" and "Friday at six" are
    # different asks.
    vol.Exclusive(ATTR_DUE_DATE, "due"): cv.date,
    vol.Exclusive(ATTR_DUE_DATETIME, "due"): cv.datetime,
}

ITEM_FIELDS = {vol.Required(ATTR_ITEM): cv.string}


async def async_setup_entry(
    hass: HomeAssistant,
    entry: HaTodoConfigEntry,
    async_add_entities: AddConfigEntryEntitiesCallback,
) -> None:
    coordinator = entry.runtime_data
    entities: list[SensorEntity] = [
        OverdueSensor(coordinator, entry),
        PendingTrackersSensor(coordinator, entry),
    ]

    entities += [
        ListSensor(coordinator, entry, summary) for summary in coordinator.data.lists
    ]

    for tracker in coordinator.data.trackers:
        if tracker.scope == SCOPE_SHARED:
            entities.append(TrackerSensor(coordinator, entry, tracker, None, None))
            continue
        # A per-member tracker gets one sensor per series: "the tracker's
        # value" is undefined when four people each have one, and a single
        # sensor would have to pick one silently.
        readings = coordinator.data.series.get(tracker.id)
        for line in readings.series if readings else ():
            entities.append(
                TrackerSensor(
                    coordinator, entry, tracker, line.member_id, line.display_name
                )
            )

    async_add_entities(entities)

    # Entity services, so the action's target is a picker rather than a name
    # somebody types. Shared across platforms of the same domain, which is why
    # each one declares required_features.
    platform = entity_platform.async_get_current_platform()
    for name, fields, method in (
        (SERVICE_ADD_ITEM, ADD_ITEM_FIELDS, "async_add_item"),
        (SERVICE_COMPLETE_ITEM, ITEM_FIELDS, "async_complete_item"),
        (SERVICE_LAPSE_ITEM, ITEM_FIELDS, "async_lapse_item"),
        (SERVICE_REOPEN_ITEM, ITEM_FIELDS, "async_reopen_item"),
    ):
        platform.async_register_entity_service(
            name,
            fields,
            method,
            required_features=[ListFeature.ITEMS],
            # The item it wrote comes back, so a script can act on it without
            # waiting for the next poll.
            supports_response=SupportsResponse.OPTIONAL,
        )


class _Base(CoordinatorEntity[HaTodoCoordinator], SensorEntity):
    _attr_has_entity_name = True

    def __init__(
        self, coordinator: HaTodoCoordinator, entry: HaTodoConfigEntry
    ) -> None:
        super().__init__(coordinator)
        self._entry_id = entry.entry_id
        self._attr_device_info = DeviceInfo(
            identifiers={(DOMAIN, entry.entry_id)},
            name=entry.title,
            manufacturer="ha-todo",
        )


class TrackerSensor(_Base):
    """The current bucket's figure for one series.

    Deliberately **no `device_class`**. A unit here is a label and never a
    conversion (adr/0051): `device_class: weight` would let Home Assistant
    render kilograms as pounds and show the household a number nobody recorded.
    """

    _attr_state_class = SensorStateClass.MEASUREMENT

    def __init__(
        self,
        coordinator: HaTodoCoordinator,
        entry: HaTodoConfigEntry,
        tracker: TrackerSummary,
        member_id: str | None,
        member_name: str | None,
    ) -> None:
        super().__init__(coordinator, entry)
        self._tracker_id = tracker.id
        self._member_id = member_id
        self._attr_name = (
            tracker.name if member_name is None else f"{tracker.name} {member_name}"
        )
        self._attr_unique_id = f"{tracker.id}_{member_id or 'household'}"
        self._attr_native_unit_of_measurement = tracker.unit
        self._attr_suggested_display_precision = tracker.decimals

    @property
    def native_value(self) -> Decimal | None:
        """The bucket's value, or None when nothing was recorded in it.

        **Never `or 0`.** A bucket nobody recorded is a null and not a zero: a
        missed weigh-in is not 0 kg, and a chart drawing it as one invents a
        crash (R196). The companion is the pending-trackers sensor, which is
        how the household finds out nothing was recorded.
        """
        readings = self.coordinator.data.series.get(self._tracker_id)
        if readings is None:
            return None
        return readings.current(self._member_id)


class OverdueSensor(_Base):
    """How much work is late (R197)."""

    _attr_translation_key = "overdue"
    _attr_state_class = SensorStateClass.MEASUREMENT

    def __init__(
        self, coordinator: HaTodoCoordinator, entry: HaTodoConfigEntry
    ) -> None:
        super().__init__(coordinator, entry)
        self._attr_unique_id = f"{entry.entry_id}_overdue"

    @property
    def native_value(self) -> int:
        # The server's own total, never the length of the page it sent: that
        # page is capped, so counting its rows under-reports, and the
        # undercount grows with the backlog.
        return self.coordinator.data.overdue


class PendingTrackersSensor(_Base):
    """Daily trackers with nothing recorded today."""

    _attr_translation_key = "pending_trackers"
    _attr_state_class = SensorStateClass.MEASUREMENT

    def __init__(
        self, coordinator: HaTodoCoordinator, entry: HaTodoConfigEntry
    ) -> None:
        super().__init__(coordinator, entry)
        self._attr_unique_id = f"{entry.entry_id}_pending_trackers"

    @property
    def native_value(self) -> int:
        return len(self.coordinator.data.pending_trackers)

    @property
    def extra_state_attributes(self) -> dict[str, Any]:
        # The names too, so a reminder can say which rather than how many.
        return {
            "trackers": [
                t.get("name") for t in self.coordinator.data.pending_trackers
            ]
        }


class ListSensor(_Base):
    """One shared list, with enough of it to draw a screen from.

    This is deliberately **not** a `todo` entity. That platform's item carries
    a summary, a status, a due date and a description and nothing else, so it
    silently dropped the assignee, the costs, the points and - worst - a whole
    third state, since it has only two. See adr/0062.

    The state is the number of open chores, because that is the one number
    worth putting on a badge or triggering an automation from. Everything else
    is an attribute.
    """

    _attr_state_class = SensorStateClass.MEASUREMENT
    _attr_native_unit_of_measurement = "chores"
    _attr_supported_features = ListFeature.ITEMS

    # The bulk of the payload is never written to the recorder database.
    #
    # Enforced here rather than asked for in the README, because asking does
    # not work: the recorder caps stored attributes at 16384 bytes and, past
    # that, logs a warning and stores `{}` - history silently empties while the
    # live state still looks right. At roughly 400 bytes a chore that cap
    # breaks at about forty, which a real household list passes without anybody
    # noticing. A deployment keeping a year of history on an SD card pays for
    # it twice, in size and in writes.
    #
    # Only the recorder is affected. Templates, automations and cards read the
    # live state and see everything. The counts stay recorded, because an
    # open-chore count over a year is the one part worth graphing.
    _unrecorded_attributes = frozenset({"items", "fields"})

    def __init__(
        self,
        coordinator: HaTodoCoordinator,
        entry: HaTodoConfigEntry,
        summary: ListSummary,
    ) -> None:
        super().__init__(coordinator, entry)
        self._list_id = summary.id
        self._attr_name = summary.name
        self._attr_unique_id = f"list_{summary.id}"
        self._description = summary.description

    @property
    def _items(self) -> list:
        return self.coordinator.data.items.get(self._list_id) or []

    @property
    def available(self) -> bool:
        # A list whose grant was revoked mid-poll is dropped from the payload
        # rather than emptied, so "no entry" is unavailable and not zero.
        return super().available and self._list_id in self.coordinator.data.items

    @property
    def native_value(self) -> int:
        return sum(1 for i in self._items if i.state == STATE_OPEN)

    @property
    def extra_state_attributes(self) -> dict[str, Any]:
        """The list, as data.

        **These entities should be excluded from the recorder.** The recorder
        caps stored attributes at 16384 bytes and, past that, logs a warning
        and stores `{}` - so history quietly empties while the live state still
        looks right. A list of chores is not meaningful history anyway. See
        the README.
        """
        zone = self.coordinator.client.timezone
        items = self._items
        return {
            "list_id": self._list_id,
            "list_name": self._attr_name,
            # The list's own description, which is what its owner wrote about
            # it - not a chore's.
            "list_description": self._description,
            "open": sum(1 for i in items if i.state == STATE_OPEN),
            "completed": sum(1 for i in items if i.state == STATE_COMPLETED),
            "lapsed": sum(1 for i in items if i.state == STATE_LAPSED),
            "overdue": sum(1 for i in items if is_overdue(i.due_at, i.state, zone)),
            "total": len(items),
            # Capped, and the cap is reported rather than hidden: a truncated
            # list that says nothing about being truncated is a screen that is
            # confidently wrong.
            "truncated": len(items) > MAX_PUBLISHED_ITEMS,
            "items": [self._published(i, zone) for i in items[:MAX_PUBLISHED_ITEMS]],
            # What the list insists on, so a form can be drawn without a
            # second request: kind, label and whether it is required.
            "fields": [
                {
                    "id": f.get("id"),
                    "kind": f.get("kind"),
                    "label": f.get("label"),
                    "required": bool(f.get("required")),
                }
                for f in (self.coordinator.data.fields.get(self._list_id) or {}).values()
            ],
        }

    def _published(self, item, zone: str) -> dict[str, Any]:
        """One chore, plus whether it is late.

        The flag is computed here rather than left to a template, because the
        boundary is the start of today in the household's zone (adr/0040) and a
        template doing `due_at < now()` would disagree with the product's own
        agenda every morning. `design/0023` lists that as a silent failure; this
        is what makes it unnecessary.
        """
        return {**item.as_dict(), "overdue": is_overdue(item.due_at, item.state, zone)}

    # -- the actions, which target this entity ----------------------------

    async def async_add_item(
        self,
        description: str,
        due_date: Any | None = None,
        due_datetime: Any | None = None,
    ) -> ServiceResponse:
        """Adds a chore to this list."""
        due = due_datetime or due_date
        try:
            item = await self.coordinator.client.create_item(
                self._list_id,
                description,
                to_instant(due, self.coordinator.client.timezone),
            )
        except HaTodoError as refused:
            # A list insisting on an assignee, a detail or a cost refuses this
            # in the product's own words, which is the only useful answer.
            raise HomeAssistantError(str(refused)) from refused
        await self.coordinator.async_request_refresh()
        return {"item": item.as_dict()}

    async def async_complete_item(self, item: str) -> ServiceResponse:
        return await self._set_state(item, STATE_COMPLETED)

    async def async_lapse_item(self, item: str) -> ServiceResponse:
        return await self._set_state(item, STATE_LAPSED)

    async def async_reopen_item(self, item: str) -> ServiceResponse:
        return await self._set_state(item, STATE_OPEN)

    def _resolve(self, wanted: str):
        """Finds a chore on **this** list by id, or by description.

        Scoping to the target list is what makes a description usable at all:
        two chores called "bins" on two different lists are no longer
        ambiguous, and two on the same list are refused by name rather than
        resolved by whichever came first.
        """
        needle = wanted.strip().casefold()
        for candidate in self._items:
            if candidate.id.casefold() == needle:
                return candidate
        matches = [c for c in self._items if c.description.casefold() == needle]
        if len(matches) == 1:
            return matches[0]
        if not matches:
            raise ServiceValidationError(
                f'No chore called "{wanted}" is on {self._attr_name}.'
            )
        raise ServiceValidationError(
            f'{len(matches)} chores on {self._attr_name} are called "{wanted}". '
            "Use the chore's id, from this sensor's items attribute."
        )

    async def _set_state(self, wanted_item: str, wanted: str) -> ServiceResponse:
        """Asks a chore to become something, and resolves one conflict.

        The version comes from the coordinator's cache, because the caller is
        given a chore and never a version - one read from an attribute a minute
        ago is exactly the stale write the server exists to refuse.
        """
        known = self._resolve(wanted_item)
        try:
            updated = await self.coordinator.client.set_item_state(
                known.id, known.version, wanted
            )
        except HaTodoStale as conflict:
            fresh = conflict.item
            if fresh is None:
                raise HomeAssistantError(str(conflict)) from conflict
            if fresh.get("state") == wanted:
                # Somebody got there a second earlier. That is the outcome the
                # caller asked for, so raising would be noise.
                _LOGGER.debug("Item %s was already %s", known.id, wanted)
                await self.coordinator.async_request_refresh()
                return {"item": fresh}
            try:
                updated = await self.coordinator.client.set_item_state(
                    known.id, fresh["version"], wanted
                )
            except HaTodoError as again:
                # Never a third attempt: two automations and a member could
                # otherwise hold a chore in a loop nobody asked for.
                raise HomeAssistantError(str(again)) from again
        except HaTodoError as refused:
            raise HomeAssistantError(str(refused)) from refused

        await self.coordinator.async_request_refresh()
        return {"item": updated.as_dict()}
