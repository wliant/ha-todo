"""The response shapes this integration consumes, and nothing more.

Deliberately partial. The product's DTOs carry assignees, costs, points,
completion notes and pictures; none of that has anywhere to go in Home
Assistant (adr/0059), so none of it is modelled here. A field added to one of
these dataclasses should be a field something on the Home Assistant side
actually reads.

Imports nothing from ``homeassistant`` - see adr/0058.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from decimal import Decimal
from typing import Any


@dataclass(frozen=True, slots=True)
class Session:
    """Who we signed in as, and the zone the household keeps.

    ``timezone`` is the *household's* (``app.timezone``), served on
    ``/api/me`` - never the caller's and never Home Assistant's. See adr/0036.
    """

    user_id: str
    username: str
    display_name: str
    timezone: str


@dataclass(frozen=True, slots=True)
class ListSummary:
    id: str
    name: str
    owned: bool
    description: str | None = None


@dataclass(frozen=True, slots=True)
class CostValue:
    """One answered cost, with the label its list declared for it.

    The label is why the list's field configuration is fetched at all: a raw
    ``field_id`` and ``choice_id`` are correct and useless to anything drawing
    a screen.
    """

    field_id: str
    label: str
    value: str | None
    points: int | None

    def as_dict(self) -> dict[str, Any]:
        return {
            "field_id": self.field_id,
            "label": self.label,
            "value": self.value,
            "points": self.points,
        }


@dataclass(frozen=True, slots=True)
class Item:
    """One chore, with everything a screen might want to draw.

    ``version`` is held but deliberately **not published**: every write goes
    through a service that reads it from here, so there is no reason for a
    caller to carry one and every reason not to - a version from an attribute
    read seconds ago is exactly the stale write the server exists to refuse.

    No ``detail_html`` and no ``completion_note_html``. Both are sanitized rich
    text with embedded images, and nothing here may carry them: see adr/0061.
    """

    id: str
    list_id: str
    description: str
    state: str
    due_at: str | None
    version: int
    assignee: str | None = None
    assignee_id: str | None = None
    self_assigned: bool = False
    points_earned: int | None = None
    completed_by: str | None = None
    completed_at: str | None = None
    lapsed_by: str | None = None
    lapsed_at: str | None = None
    has_detail: bool = False
    costs: tuple[CostValue, ...] = ()

    @classmethod
    def from_json(
        cls, payload: dict[str, Any], fields: dict[str, Any] | None = None
    ) -> Item:
        def who(key: str) -> tuple[str | None, str | None]:
            member = payload.get(key)
            if not member:
                return None, None
            return member.get("displayName"), member.get("id")

        assignee, assignee_id = who("assignee")
        completed_by, _ = who("completedBy")
        lapsed_by, _ = who("lapsedBy")
        return cls(
            id=payload["id"],
            list_id=payload["listId"],
            description=payload["description"],
            state=payload["state"],
            due_at=payload.get("dueAt"),
            version=payload["version"],
            assignee=assignee,
            assignee_id=assignee_id,
            self_assigned=bool(payload.get("selfAssigned")),
            points_earned=payload.get("pointsEarned"),
            completed_by=completed_by,
            completed_at=payload.get("completedAt"),
            lapsed_by=lapsed_by,
            lapsed_at=payload.get("lapsedAt"),
            # Whether there *is* a detail, never the detail itself - so a
            # screen can offer to open the item in the product.
            has_detail=bool(payload.get("detailHtml")),
            costs=tuple(
                _cost(value, fields or {})
                for value in (payload.get("costValues") or [])
            ),
        )

    def as_dict(self) -> dict[str, Any]:
        """The published shape. Keep it lean - see sensor.py on attributes."""
        return {
            "id": self.id,
            "description": self.description,
            "state": self.state,
            "due_at": self.due_at,
            "assignee": self.assignee,
            "assignee_id": self.assignee_id,
            "self_assigned": self.self_assigned,
            "points_earned": self.points_earned,
            "completed_by": self.completed_by,
            "completed_at": self.completed_at,
            "lapsed_by": self.lapsed_by,
            "lapsed_at": self.lapsed_at,
            "has_detail": self.has_detail,
            "costs": [c.as_dict() for c in self.costs],
        }


def _cost(value: dict[str, Any], fields: dict[str, Any]) -> CostValue:
    """Resolves one answered cost against the list's declared fields."""
    field_id = value.get("fieldId", "")
    field = fields.get(field_id) or {}
    label = field.get("label") or "Cost"
    choice_id = value.get("choiceId")
    if choice_id:
        choice = next(
            (c for c in field.get("choices") or [] if c.get("id") == choice_id), {}
        )
        return CostValue(field_id, label, choice.get("label"), choice.get("points"))
    amount = value.get("amount")
    if amount is None:
        return CostValue(field_id, label, None, None)
    unit = field.get("costUnitLabel")
    return CostValue(
        field_id, label, f"{amount} {unit}".strip() if unit else str(amount), None
    )


@dataclass(frozen=True, slots=True)
class CollectionSummary:
    id: str
    name: str


@dataclass(frozen=True, slots=True)
class TrackerSummary:
    id: str
    name: str
    unit: str
    period: str
    scope: str
    rollup: str
    decimals: int

    @classmethod
    def from_json(cls, payload: dict[str, Any]) -> TrackerSummary:
        return cls(
            id=payload["id"],
            name=payload["name"],
            unit=payload["unit"],
            period=payload["period"],
            scope=payload["scope"],
            rollup=payload["rollup"],
            decimals=int(payload.get("decimals") or 0),
        )


@dataclass(frozen=True, slots=True)
class TrackerSeries:
    """One line of a tracker's chart.

    ``member_id`` is ``None`` on a shared tracker and *means* "the
    household's" - there is no companion flag, for the reason
    ``password_hash`` has none (adr/0052).
    """

    member_id: str | None
    display_name: str
    values: list[Decimal | None] = field(default_factory=list)


@dataclass(frozen=True, slots=True)
class TrackerReadings:
    tracker_id: str
    buckets: list[str] = field(default_factory=list)
    series: list[TrackerSeries] = field(default_factory=list)

    @classmethod
    def from_json(cls, payload: dict[str, Any]) -> TrackerReadings:
        return cls(
            tracker_id=payload["trackerId"],
            buckets=list(payload.get("buckets") or []),
            series=[
                TrackerSeries(
                    member_id=line.get("memberId"),
                    display_name=line.get("displayName") or "",
                    # A bucket nobody recorded is null and never zero, so it
                    # stays None all the way to native_value. See R196.
                    values=[
                        None if v is None else Decimal(str(v))
                        for v in (line.get("values") or [])
                    ],
                )
                for line in (payload.get("series") or [])
            ],
        )

    def current(self, member_id: str | None) -> Decimal | None:
        """The value of the bucket on the end of the axis, or None.

        The axis is asked for starting at the current bucket, so the last
        element *is* "now". It is deliberately not "the most recent bucket that
        happens to hold a number": a weight sensor that keeps reporting
        Tuesday's figure on Friday is answering a question nobody asked, and
        R196 is that an unrecorded bucket says nothing. The companion is the
        pending-trackers sensor, which is how the household finds out.
        """
        for line in self.series:
            if line.member_id == member_id:
                return line.values[-1] if line.values else None
        return None
