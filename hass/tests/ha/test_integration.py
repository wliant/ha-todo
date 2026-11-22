"""Loading the integration, the sensors it produces, and the services.

There is deliberately no `todo` entity to assert on: a list is a rich sensor
plus explicit services (adr/0062).
"""

from __future__ import annotations

from datetime import date
from decimal import Decimal
from unittest.mock import AsyncMock, patch

import pytest
from homeassistant.config_entries import ConfigEntryState
from homeassistant.core import HomeAssistant
from homeassistant.exceptions import HomeAssistantError, ServiceValidationError
from pytest_homeassistant_custom_component.common import MockConfigEntry

from custom_components.ha_todo.const import DOMAIN
from custom_components.ha_todo.models import (
    CollectionSummary,
    Item,
    ListSummary,
    Session,
    TrackerReadings,
    TrackerSeries,
    TrackerSummary,
)

LIST = "sensor.mock_title_groceries"

ENTRY = {
    "url": "https://todo.example.invalid",
    "username": "homeassistant",
    "password": "secret",
    "verify_ssl": True,
}

IDENTITY = Session(
    user_id="user-1",
    username="homeassistant",
    display_name="Home Assistant",
    timezone="Asia/Singapore",
)

FIELDS = {
    "f1": {"id": "f1", "kind": "DESCRIPTION", "label": "What", "required": True},
    "f2": {
        "id": "f2",
        "kind": "COST",
        "label": "Effort",
        "required": False,
        "costMode": "FIXED",
        "choices": [{"id": "c1", "label": "Quick", "points": 2}],
    },
}


def _items() -> list[Item]:
    return [
        Item.from_json(
            {
                "id": "i1",
                "listId": "l1",
                "description": "Milk",
                "state": "OPEN",
                "dueAt": None,
                "version": 1,
                "assignee": {"id": "m1", "displayName": "Ana"},
                "costValues": [{"fieldId": "f2", "choiceId": "c1"}],
            },
            FIELDS,
        ),
        Item.from_json(
            {
                "id": "i2",
                "listId": "l1",
                "description": "Bins",
                "state": "COMPLETED",
                "dueAt": None,
                "version": 4,
                "completedBy": {"id": "m2", "displayName": "Ben"},
                "pointsEarned": 2,
            },
            FIELDS,
        ),
        # Planned, and nobody got to it. The native platform could not say so.
        Item.from_json(
            {
                "id": "i3",
                "listId": "l1",
                "description": "Gutters",
                "state": "LAPSED",
                # Yesterday, so it would be overdue if a lapse could be.
                "dueAt": "2020-01-01T00:00:00Z",
                "version": 2,
                "lapsedBy": {"id": "m1", "displayName": "Ana"},
            },
            FIELDS,
        ),
    ]


@pytest.fixture
def client():
    with patch("custom_components.ha_todo.HaTodoClient", autospec=True) as factory:
        fake = factory.return_value
        fake.login = AsyncMock(return_value=IDENTITY)
        fake.close = AsyncMock()
        type(fake).timezone = "Asia/Singapore"
        fake.lists = AsyncMock(
            return_value=[ListSummary("l1", "Groceries", True, "What we need")]
        )
        fake.fields = AsyncMock(return_value=FIELDS)
        fake.items = AsyncMock(return_value=_items())
        fake.collections = AsyncMock(return_value=[CollectionSummary("c1", "Recipes")])
        fake.trackers = AsyncMock(
            return_value=[
                TrackerSummary("t1", "Weight", "kg", "DAY", "SHARED", "LATEST", 1)
            ]
        )
        fake.series = AsyncMock(
            return_value=TrackerReadings(
                "t1",
                ["2026-11-20"],
                [TrackerSeries(None, "", [Decimal("64.2")])],
            )
        )
        fake.pending_trackers = AsyncMock(
            return_value=[{"id": "t9", "name": "Steps"}]
        )
        fake.overdue_total = AsyncMock(return_value=9)
        fake.create_item = AsyncMock(
            return_value=Item("new", "l1", "Eggs", "OPEN", None, 1)
        )
        fake.set_item_state = AsyncMock(
            return_value=Item("i1", "l1", "Milk", "COMPLETED", None, 2)
        )
        fake.record_reading = AsyncMock(return_value={"amount": "64.5"})
        fake.add_entry = AsyncMock(return_value={"name": "Chicken rice"})
        yield fake


async def _load(hass: HomeAssistant) -> MockConfigEntry:
    entry = MockConfigEntry(domain=DOMAIN, data=ENTRY, unique_id="user-1")
    entry.add_to_hass(hass)
    assert await hass.config_entries.async_setup(entry.entry_id)
    await hass.async_block_till_done()
    return entry


async def test_the_integration_loads_and_makes_a_list_a_sensor(
    hass: HomeAssistant, client
):
    entry = await _load(hass)
    assert entry.state is ConfigEntryState.LOADED
    state = hass.states.get("sensor.mock_title_groceries")
    assert state is not None
    # The state is the open count: one of the three.
    assert state.state == "1"


async def test_there_is_no_native_todo_entity(hass: HomeAssistant, client):
    """adr/0062. The whole point is that a list is not a `todo` entity."""
    await _load(hass)
    assert not [e for e in hass.states.async_entity_ids() if e.startswith("todo.")]


async def test_the_sensor_publishes_all_three_states(hass: HomeAssistant, client):
    await _load(hass)
    attrs = hass.states.get("sensor.mock_title_groceries").attributes
    assert (attrs["open"], attrs["completed"], attrs["lapsed"]) == (1, 1, 1)
    assert attrs["total"] == 3
    states = {i["description"]: i["state"] for i in attrs["items"]}
    # Including the lapse, which the native platform had to drop entirely.
    assert states == {"Milk": "OPEN", "Bins": "COMPLETED", "Gutters": "LAPSED"}


async def test_each_chore_says_whether_it_is_late(hass: HomeAssistant, client):
    """So a template never has to recompute the day boundary itself."""
    await _load(hass)
    items = {i["description"]: i for i in hass.states.get(LIST).attributes["items"]}
    assert items["Milk"]["overdue"] is False      # no due date
    assert items["Gutters"]["overdue"] is False   # dated 2020 but lapsed


async def test_the_pending_sensor_names_the_trackers(hass: HomeAssistant, client):
    await _load(hass)
    state = hass.states.get("sensor.mock_title_trackers_pending_today")
    assert state.state == "1"
    assert state.attributes["trackers"] == ["Steps"]


async def test_a_lapsed_chore_is_never_counted_overdue(hass: HomeAssistant, client):
    await _load(hass)
    attrs = hass.states.get("sensor.mock_title_groceries").attributes
    # "Gutters" is dated 2020 but lapsed, so nobody is waiting on it.
    assert attrs["overdue"] == 0


async def test_the_sensor_carries_what_the_todo_platform_dropped(
    hass: HomeAssistant, client
):
    await _load(hass)
    items = {i["description"]: i for i in
             hass.states.get("sensor.mock_title_groceries").attributes["items"]}
    assert items["Milk"]["assignee"] == "Ana"
    assert items["Bins"]["completed_by"] == "Ben"
    assert items["Bins"]["points_earned"] == 2
    # A cost, with the label and points its list declared for it.
    assert items["Milk"]["costs"] == [
        {"field_id": "f2", "label": "Effort", "value": "Quick", "points": 2}
    ]


async def test_the_sensor_never_publishes_rich_text(hass: HomeAssistant, client):
    """adr/0061 still holds: the detail is not carried, only whether there is one."""
    await _load(hass)
    attrs = hass.states.get("sensor.mock_title_groceries").attributes
    for item in attrs["items"]:
        assert "detail_html" not in item
        assert "completion_note_html" not in item
        assert "version" not in item
        assert "has_detail" in item


async def test_the_sensor_publishes_the_list_s_own_description(
    hass: HomeAssistant, client
):
    """The list's description, which is a different thing from a chore's."""
    await _load(hass)
    attrs = hass.states.get(LIST).attributes
    assert attrs["list_name"] == "Groceries"
    assert attrs["list_description"] == "What we need"


async def test_items_carry_the_assignee_so_a_template_can_filter_by_it(
    hass: HomeAssistant, client
):
    """R203, and the thing an automation actually wants to do with it."""
    await _load(hass)
    items = hass.states.get(LIST).attributes["items"]
    mine = [i["description"] for i in items if i["assignee"] == "Ana"]
    assert mine == ["Milk"]
    # The stable id is published alongside the name, because a display name is
    # what somebody renames.
    assert [i["assignee_id"] for i in items if i["assignee"] == "Ana"] == ["m1"]
    assert [i["description"] for i in items if i["assignee"] is None] == [
        "Bins",
        "Gutters",
    ]


async def test_the_bulk_of_the_payload_is_kept_out_of_the_recorder(
    hass: HomeAssistant, client
):
    """The 16KB cap fails silently, so this is enforced rather than documented.

    Past it the recorder logs a warning and stores `{}` - history empties while
    the live state still looks right. At ~400 bytes a chore that is about forty
    chores, which a real list passes unnoticed.
    """
    from custom_components.ha_todo.sensor import ListSensor

    await _load(hass)
    assert ListSensor._unrecorded_attributes == frozenset({"items", "fields"})
    # Still live for templates and automations - only the database misses them.
    attrs = hass.states.get(LIST).attributes
    assert attrs["items"] and attrs["fields"]
    # The counts stay recorded: they are the part worth graphing.
    for kept in ("open", "completed", "lapsed", "overdue", "total"):
        assert kept in attrs


async def test_the_sensor_describes_the_fields_a_form_would_need(
    hass: HomeAssistant, client
):
    await _load(hass)
    fields = hass.states.get("sensor.mock_title_groceries").attributes["fields"]
    assert {f["label"]: f["required"] for f in fields} == {
        "What": True,
        "Effort": False,
    }


async def test_adding_a_chore_sends_the_household_s_instant(
    hass: HomeAssistant, client
):
    """A bare date is 23:59 in Asia/Singapore, which is 15:59Z."""
    await _load(hass)
    result = await hass.services.async_call(
        DOMAIN,
        "add_item",
        {"description": "Eggs", "due_date": date(2026, 11, 20)},
        target={"entity_id": LIST},
        blocking=True,
        return_response=True,
    )
    client.create_item.assert_awaited_once_with("l1", "Eggs", "2026-11-20T15:59:00Z")
    assert result[LIST]["item"]["description"] == "Eggs"


async def test_the_actions_target_an_entity_rather_than_a_typed_name(
    hass: HomeAssistant, client
):
    """The reason these are entity services: the target is a picker.

    An unknown list is now unexpressable rather than an error message.
    """
    await _load(hass)
    for service in ("add_item", "complete_item", "lapse_item", "reopen_item"):
        assert hass.services.has_service(DOMAIN, service)
    with pytest.raises(Exception):
        # No target at all is refused by Home Assistant before we see it.
        await hass.services.async_call(
            DOMAIN, "add_item", {"description": "x"}, blocking=True
        )


async def test_a_sensor_that_is_not_a_list_does_not_offer_the_actions(
    hass: HomeAssistant, client
):
    """required_features is what keeps these off the tracker and count sensors."""
    await _load(hass)
    with pytest.raises(HomeAssistantError):
        await hass.services.async_call(
            DOMAIN,
            "add_item",
            {"description": "x"},
            target={"entity_id": "sensor.mock_title_overdue"},
            blocking=True,
        )
    client.create_item.assert_not_awaited()


@pytest.mark.parametrize(
    ("service", "wanted"),
    [
        ("complete_item", "COMPLETED"),
        ("lapse_item", "LAPSED"),
        ("reopen_item", "OPEN"),
    ],
)
async def test_each_state_change_sends_the_cached_version(
    hass: HomeAssistant, client, service, wanted
):
    await _load(hass)
    await hass.services.async_call(
        DOMAIN, service, {"item": "i1"}, target={"entity_id": LIST}, blocking=True
    )
    client.set_item_state.assert_awaited_once_with("i1", 1, wanted)


async def test_a_chore_can_be_named_by_its_description(hass: HomeAssistant, client):
    """Scoping to the target list is what makes a description usable."""
    await _load(hass)
    await hass.services.async_call(
        DOMAIN, "complete_item", {"item": "milk"},
        target={"entity_id": LIST}, blocking=True,
    )
    client.set_item_state.assert_awaited_once_with("i1", 1, "COMPLETED")


async def test_an_ambiguous_description_is_refused_rather_than_guessed(
    hass: HomeAssistant, client
):
    twins = [
        Item.from_json({"id": "a", "listId": "l1", "description": "Bins",
                        "state": "OPEN", "dueAt": None, "version": 1}, FIELDS),
        Item.from_json({"id": "b", "listId": "l1", "description": "Bins",
                        "state": "OPEN", "dueAt": None, "version": 1}, FIELDS),
    ]
    client.items = AsyncMock(return_value=twins)
    await _load(hass)
    with pytest.raises(ServiceValidationError) as refused:
        await hass.services.async_call(
            DOMAIN, "complete_item", {"item": "Bins"},
            target={"entity_id": LIST}, blocking=True,
        )
    assert "2 chores" in str(refused.value)
    client.set_item_state.assert_not_awaited()


async def test_an_unknown_chore_is_refused_before_any_write(
    hass: HomeAssistant, client
):
    await _load(hass)
    with pytest.raises(ServiceValidationError):
        await hass.services.async_call(
            DOMAIN, "complete_item", {"item": "nope"},
            target={"entity_id": LIST}, blocking=True,
        )
    client.set_item_state.assert_not_awaited()


async def test_a_chore_already_in_the_wanted_state_is_not_an_error(
    hass: HomeAssistant, client
):
    """Somebody got there a second earlier; that is the outcome asked for."""
    from custom_components.ha_todo.api import HaTodoStale

    await _load(hass)
    client.set_item_state = AsyncMock(
        side_effect=HaTodoStale(
            "Ana completed this first.",
            {"id": "i1", "state": "COMPLETED", "version": 7},
        )
    )
    result = await hass.services.async_call(
        DOMAIN, "complete_item", {"item": "i1"},
        target={"entity_id": LIST}, blocking=True, return_response=True,
    )
    assert result[LIST]["item"]["state"] == "COMPLETED"
    # One attempt, and no retry: it is already what was wanted.
    assert client.set_item_state.await_count == 1


async def test_a_conflict_retries_once_with_the_fresh_version(
    hass: HomeAssistant, client
):
    from custom_components.ha_todo.api import HaTodoStale

    await _load(hass)
    client.set_item_state = AsyncMock(
        side_effect=[
            HaTodoStale("Ana renamed this.", {"id": "i1", "state": "OPEN", "version": 9}),
            Item("i1", "l1", "Milk", "COMPLETED", None, 10),
        ]
    )
    await hass.services.async_call(
        DOMAIN, "complete_item", {"item": "i1"},
        target={"entity_id": LIST}, blocking=True,
    )
    assert client.set_item_state.await_count == 2
    assert client.set_item_state.await_args.args == ("i1", 9, "COMPLETED")


async def test_the_overdue_and_pending_sensors_report_the_server_s_figures(
    hass: HomeAssistant, client
):
    await _load(hass)
    assert hass.states.get("sensor.mock_title_overdue").state == "9"
    assert hass.states.get("sensor.mock_title_trackers_pending_today").state == "1"


async def test_a_tracker_reads_its_current_bucket_with_its_own_unit(
    hass: HomeAssistant, client
):
    await _load(hass)
    state = hass.states.get("sensor.mock_title_weight")
    assert state.state == "64.2"
    assert state.attributes["unit_of_measurement"] == "kg"
    # A unit is a label and never a conversion (adr/0051).
    assert "device_class" not in state.attributes


async def test_an_unrecorded_bucket_says_nothing_rather_than_zero(
    hass: HomeAssistant, client
):
    client.series = AsyncMock(
        return_value=TrackerReadings("t1", ["2026-11-20"], [TrackerSeries(None, "", [None])])
    )
    await _load(hass)
    assert hass.states.get("sensor.mock_title_weight").state == "unknown"


async def test_recording_a_reading_defaults_to_the_household_s_today(
    hass: HomeAssistant, client
):
    await _load(hass)
    await hass.services.async_call(
        DOMAIN, "record_tracker_reading", {"tracker": "Weight", "amount": 64.5},
        blocking=True,
    )
    assert isinstance(client.record_reading.await_args.args[2], date)


async def test_adding_a_collection_entry_resolves_by_name(
    hass: HomeAssistant, client
):
    await _load(hass)
    await hass.services.async_call(
        DOMAIN,
        "add_collection_entry",
        {"collection": "recipes", "name": "Chicken rice", "tags": ["dinner"]},
        blocking=True,
    )
    client.add_entry.assert_awaited_once_with("c1", "Chicken rice", None, ["dinner"])
