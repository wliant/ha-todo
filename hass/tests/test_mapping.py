"""The two conversions, across the zones that would hide a mistake."""

from __future__ import annotations

from datetime import UTC, date, datetime

import pytest

from _pure import mapping as m

pytestmark = pytest.mark.mapping


@pytest.mark.parametrize(
    ("zone", "expected"),
    [
        ("Asia/Singapore", "2026-11-20T15:59:00Z"),
        ("Europe/London", "2026-11-20T23:59:00Z"),
        ("America/New_York", "2026-11-21T04:59:00Z"),
        ("Pacific/Auckland", "2026-11-20T10:59:00Z"),
    ],
)
def test_a_bare_date_is_the_end_of_that_day_in_the_household_s_zone(zone, expected):
    """Never midnight: that belongs to the day before the one somebody meant."""
    assert m.to_instant(date(2026, 11, 20), zone) == expected


def test_a_naive_datetime_is_read_as_the_household_s_wall_clock():
    assert m.to_instant(datetime(2026, 11, 20, 9, 0), "Asia/Singapore") == (
        "2026-11-20T01:00:00Z"
    )


def test_an_aware_datetime_keeps_the_instant_it_already_names():
    assert m.to_instant(
        datetime(2026, 11, 20, 9, 0, tzinfo=UTC), "Asia/Singapore"
    ) == "2026-11-20T09:00:00Z"


def test_the_household_s_zone_decides_the_day_not_the_machine_s(monkeypatch):
    """The bug adr/0036 exists to prevent, in the one place it could return.

    A date typed on a Home Assistant set to London, for a household in
    Singapore, must be the household's Friday - not London's.
    """
    monkeypatch.setenv("TZ", "Europe/London")
    assert m.to_instant(date(2026, 11, 20), "Asia/Singapore") == (
        "2026-11-20T15:59:00Z"
    )


def test_a_due_date_survives_the_round_trip():
    instant = m.to_instant(date(2026, 11, 20), "Asia/Singapore")
    back = m.from_instant(instant, "Asia/Singapore")
    assert (back.year, back.month, back.day, back.hour, back.minute) == (
        2026,
        11,
        20,
        23,
        59,
    )


def test_an_absent_due_date_stays_absent():
    assert m.to_instant(None, "Asia/Singapore") is None
    assert m.from_instant(None, "Asia/Singapore") is None


@pytest.mark.parametrize(
    ("due", "state", "expected"),
    [
        # Yesterday, still open: late.
        ("2026-11-21T15:59:00Z", "OPEN", True),
        # Today at 23:59: not late.
        ("2026-11-22T15:59:00Z", "OPEN", False),
        # Today at 09:00 - the case that matters. Day granularity, not
        # `< now()`, so it stays today's until midnight rather than going late
        # over breakfast (adr/0040).
        ("2026-11-22T01:00:00Z", "OPEN", False),
        # Either terminal state is never late.
        ("2026-11-21T15:59:00Z", "COMPLETED", False),
        ("2026-11-21T15:59:00Z", "LAPSED", False),
        # No date, nothing to be late for.
        (None, "OPEN", False),
    ],
)
def test_overdue_is_a_day_boundary_in_the_household_s_zone(due, state, expected, monkeypatch):
    import time

    # Pin "now" to 2026-11-22 in the household's zone by freezing the clock.
    class _FixedDatetime(m.datetime):
        @classmethod
        def now(cls, tz=None):
            return cls(2026, 11, 22, 12, 0, tzinfo=tz)

    monkeypatch.setattr(m, "datetime", _FixedDatetime)
    assert m.is_overdue(due, state, "Asia/Singapore") is expected


def test_the_three_states_are_named_not_inferred():
    """A lapse is its own value now: the sensor has no two-status limit."""
    assert (m.STATE_OPEN, m.STATE_COMPLETED, m.STATE_LAPSED) == (
        "OPEN",
        "COMPLETED",
        "LAPSED",
    )
