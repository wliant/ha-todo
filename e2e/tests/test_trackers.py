"""
Trackers (requirements/0019): numbers a household keeps, and the shape they
make.

Two of these use a second browser context, which is what this suite exists
for: a per-member tracker is exactly the thing that looks right in one session
and is wrong across two.

A note on naming the test data. The sidebar's third group is a link called
"Trackers", so a fixture name containing that word makes every
``get_by_role("link", name="Trackers")`` a strict-mode violation for the rest
of the session. The names below deliberately avoid it - this suite has already
been bitten once by exactly that, with "Agenda".
"""

from __future__ import annotations

import re
from datetime import datetime

import pytest
from playwright.sync_api import Page, expect

from conftest import DemoMember, TestData, ui_sign_in


def create_tracker(
    page: Page,
    name: str,
    *,
    unit: str = "kg",
    period: str = "Daily",
    scope: str = "One each",
    share_with: str | None = None,
) -> None:
    """Creates a tracker through the UI and lands on it."""
    page.get_by_role("link", name="Trackers", exact=True).first.click()
    page.get_by_role("link", name="New tracker").click()
    page.get_by_role("textbox", name="Name", exact=True).fill(name)
    page.get_by_role("textbox", name="Unit").fill(unit)
    page.get_by_role("radio", name=period).check()
    page.get_by_role("radio", name=scope).check()
    if share_with:
        page.get_by_role("checkbox", name=share_with).click()
    page.get_by_role("button", name="Create tracker").click()
    expect(page.get_by_role("heading", name=name)).to_be_visible()


def record(page: Page, amount: str) -> None:
    """Records a number for the bucket the form opens on, which is the current one."""
    page.get_by_role("button", name="Record", exact=True).click()
    dialog = page.get_by_role("dialog")
    dialog.get_by_role("spinbutton", name=re.compile("^Amount")).fill(amount)
    dialog.get_by_role("button", name="Save").click()
    expect(page.get_by_role("dialog")).to_have_count(0)


@pytest.mark.trackers
def test_a_member_records_a_number_and_reads_it_back(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_trackers
) -> None:
    """R159, R162, R167: declare a shape, record into it, see the figure."""
    name = test_data.name("Weighing")
    cleanup_trackers(name)
    ui_sign_in(page, base_url, demo_member)
    create_tracker(page, name)

    record(page, "72.4")

    # The headline, and the row underneath it.
    expect(page.get_by_text("72.4", exact=True)).to_be_visible()
    expect(page.get_by_role("button", name="Edit the reading of 72.4")).to_be_visible()


@pytest.mark.trackers
def test_a_monthly_tracker_asks_for_a_month_and_adds_its_numbers_up(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_trackers
) -> None:
    """
    R163: several numbers may share a bucket, and the declared rollup folds
    them - which is the whole grocery case.
    """
    name = test_data.name("Spending")
    cleanup_trackers(name)
    ui_sign_in(page, base_url, demo_member)
    create_tracker(
        page, name, unit="SGD", period="Monthly", scope="The household's"
    )
    # A monthly tracker's rollup has to be the total rather than the last
    # number, or two shopping trips would read as one.
    page.get_by_role("link", name="Settings").click()
    page.get_by_role("combobox", name="If there are several in one period").click()
    page.get_by_role("option", name=re.compile("Add them up")).click()
    page.get_by_role("button", name="Save").click()
    expect(page.get_by_role("heading", name=name)).to_be_visible()

    record(page, "142")
    record(page, "318")

    # Two rows, one bucket, one folded figure.
    expect(page.get_by_role("button", name="Edit the reading of 142.0")).to_be_visible()
    expect(page.get_by_role("button", name="Edit the reading of 318.0")).to_be_visible()
    expect(page.get_by_text("460.0", exact=True)).to_be_visible()


@pytest.mark.trackers
def test_a_tracker_shared_with_nobody_is_invisible_to_everybody_else(
    page: Page,
    second_member_page,
    base_url: str,
    demo_member: DemoMember,
    passwordless_member: DemoMember,
    test_data: TestData,
    cleanup_trackers,
) -> None:
    """R164: privacy is the grant, exactly as it is for a list."""
    name = test_data.name("Private weighing")
    cleanup_trackers(name)
    ui_sign_in(page, base_url, demo_member)
    create_tracker(page, name)
    record(page, "72.4")

    ui_sign_in(second_member_page, base_url, passwordless_member)
    second_member_page.get_by_role("link", name="Trackers", exact=True).first.click()

    expect(second_member_page.get_by_text(name)).to_have_count(0)


@pytest.mark.trackers
def test_two_members_keep_their_own_series_on_one_tracker(
    page: Page,
    second_member_page,
    base_url: str,
    demo_member: DemoMember,
    passwordless_member: DemoMember,
    test_data: TestData,
    cleanup_trackers,
) -> None:
    """
    R160, R164, R165: a per-member tracker overlays both series, and neither
    member may correct the other's number.
    """
    name = test_data.name("Shared weighing")
    cleanup_trackers(name)
    ui_sign_in(page, base_url, demo_member)
    create_tracker(page, name, share_with=passwordless_member.display_name)
    record(page, "72.4")

    ui_sign_in(second_member_page, base_url, passwordless_member)
    second_member_page.get_by_role("link", name="Trackers", exact=True).first.click()
    second_member_page.get_by_role("link", name=name).first.click()
    record(second_member_page, "81")

    # Each sees both numbers - access to the tracker is access to its series.
    # Matched with the unit, because the chart's own y-axis ticks read "72.4"
    # too: nothing in this suite asserts on chart internals (adr/0053), and a
    # bare figure is exactly how that happens by accident.
    expect(
        second_member_page.get_by_role("button", name="Edit the reading of 81.0")
    ).to_be_visible()
    expect(second_member_page.get_by_text("72.4 kg")).to_be_visible()
    # And cannot touch the other member's. No control at all, rather than one
    # that 403s.
    expect(
        second_member_page.get_by_role("button", name="Edit the reading of 72.4")
    ).to_have_count(0)


@pytest.mark.trackers
def test_a_daily_tracker_nudges_the_agenda_until_it_is_recorded(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_trackers
) -> None:
    """
    R171: the only place a tracker reaches a planning surface, and it clears
    itself.
    """
    name = test_data.name("Nudging")
    cleanup_trackers(name)
    ui_sign_in(page, base_url, demo_member)
    create_tracker(page, name)

    page.get_by_role("link", name="Agenda").click()
    notice = page.get_by_text(f"Nothing recorded today for {name}")
    expect(notice).to_be_visible()

    page.get_by_role("button", name="Record", exact=True).click()
    dialog = page.get_by_role("dialog")
    dialog.get_by_role("spinbutton", name=re.compile("^Amount")).fill("72.4")
    dialog.get_by_role("button", name="Save").click()

    # The strip goes once there is a number for today, rather than lingering
    # until the page is reloaded.
    expect(notice).to_have_count(0)


@pytest.mark.trackers
def test_a_tracker_can_be_pinned_and_reaches_the_sidebar(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_trackers
) -> None:
    """R170: the same star, the same section, as the other two kinds."""
    name = test_data.name("Pinning numbers")
    cleanup_trackers(name)
    ui_sign_in(page, base_url, demo_member)
    create_tracker(page, name)

    page.get_by_role("link", name="Trackers", exact=True).first.click()
    page.get_by_role("button", name=f"Pin {name}").click()

    pinned = page.get_by_role("list", name="Pinned")
    expect(pinned.get_by_role("link", name=name)).to_be_visible()


@pytest.mark.trackers
def test_a_trackers_shape_is_fixed_once_it_holds_numbers(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_trackers
) -> None:
    """
    R161: changing a month tracker into a day tracker would strand every
    stored bucket, so the settings screen stops offering it rather than
    offering a control that is refused.
    """
    name = test_data.name("Fixing shape")
    cleanup_trackers(name)
    ui_sign_in(page, base_url, demo_member)
    create_tracker(page, name)

    page.get_by_role("link", name="Settings").click()
    expect(page.get_by_role("radio", name="Monthly")).to_be_visible()
    page.get_by_role("link", name="Cancel").click()

    record(page, "72.4")
    page.get_by_role("link", name="Settings").click()

    expect(page.get_by_role("radio", name="Monthly")).to_have_count(0)
    expect(
        page.get_by_text("This tracker already holds numbers", exact=False)
    ).to_be_visible()
