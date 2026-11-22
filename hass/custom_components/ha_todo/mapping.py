"""The conversions between this product and Home Assistant.

Each of them works for months and is then wrong once, which is why they live
here together and are tested without Home Assistant present at all.

Imports nothing from ``homeassistant`` - not even ``homeassistant.util.dt``,
whose ``as_utc`` would do the job. That one convenience import would drag the
whole framework into the pure tests. See adr/0058.
"""

from __future__ import annotations

from datetime import UTC, date, datetime
from zoneinfo import ZoneInfo

# The time a bare date carries, matching web/src/items/DueAtField.tsx.
#
# End of day, not midnight: midnight belongs to the day *before* the one
# somebody meant, so a chore dated Thursday would sit on Wednesday's agenda row
# and read as overdue by Thursday lunchtime.
END_OF_DAY = (23, 59)

#: The product's three item states.
STATE_OPEN = "OPEN"
STATE_COMPLETED = "COMPLETED"
STATE_LAPSED = "LAPSED"


def to_instant(due: date | datetime | None, timezone: str) -> str | None:
    """A Home Assistant due date as the instant the product stores.

    ``timezone`` is the **household's**, from ``/api/me`` - never
    ``hass.config.time_zone``. A Home Assistant configured for somewhere else
    must not be able to file a chore on the wrong day; that is the whole of
    adr/0036.

    A bare ``date`` becomes 23:59 of that day in the household's zone. A
    ``datetime`` keeps its time: a real appointment means what it says.
    """
    if due is None:
        return None
    zone = ZoneInfo(timezone)
    if isinstance(due, datetime):
        local = due.astimezone(zone) if due.tzinfo else due.replace(tzinfo=zone)
    else:
        local = datetime(due.year, due.month, due.day, *END_OF_DAY, tzinfo=zone)
    return local.astimezone(UTC).isoformat().replace("+00:00", "Z")


def from_instant(instant: str | None, timezone: str) -> datetime | None:
    """The product's instant as a datetime in the household's zone.

    Always a ``datetime`` and never a ``date``, even when the time is 23:59:
    presenting the default as a bare date would be lossy in the other
    direction, because a chore genuinely due at 23:59 exists.
    """
    if not instant:
        return None
    parsed = datetime.fromisoformat(instant.replace("Z", "+00:00"))
    return parsed.astimezone(ZoneInfo(timezone))


def today_in(timezone: str) -> date:
    """The household's date, for a reading that did not name one.

    Sent raw: ``TrackerReadingService`` canonicalises it through
    ``TrackerPeriod.startOf``, so the server decides which week it was. A
    client-side copy of that rule is a second definition of where a week
    starts.
    """
    return datetime.now(ZoneInfo(timezone)).date()


def is_overdue(due_at: str | None, state: str, timezone: str) -> bool:
    """Whether an open chore's day has gone, in the household's zone.

    **Day granularity, not ``< now()``** - the rule adr/0040 sets for the
    product's own overdue strip. A chore due at 09:00 stays today's until
    midnight rather than becoming late over breakfast, which is the only
    definition that matches what the household sees on its own screens.

    A chore in either terminal state is never overdue: it is finished, or
    somebody decided nobody was going to get to it.
    """
    if state != STATE_OPEN or not due_at:
        return False
    zone = ZoneInfo(timezone)
    due = datetime.fromisoformat(due_at.replace("Z", "+00:00")).astimezone(zone)
    today = datetime.now(zone).date()
    return due.date() < today
