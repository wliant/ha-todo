"""
The dashboard (requirements/0005 R44, R45).

The first surface that reads across lists, so the test that matters most is the
negative one: a private list must not appear on somebody else's dashboard, in
any view.
"""

from __future__ import annotations

import re

from datetime import datetime, timedelta

import pytest
from playwright.sync_api import Page, expect

from conftest import DemoMember, TestData, ui_sign_in


def create_list(page: Page, name: str, share_with: str | None = None) -> str:
    # "New list" lives on the list of lists, and signing in no longer lands
    # there - the dashboard does (R44). The sidebar is always present.
    page.get_by_role("link", name="Lists").first.click()
    page.get_by_role("link", name="New list").click()
    page.get_by_role("textbox", name="Name", exact=True).fill(name)
    if share_with:
        page.get_by_role("checkbox", name=share_with).click()
    page.get_by_role("button", name="Create list").click()
    expect(page.get_by_role("heading", name=name)).to_be_visible()
    return page.url.rsplit("/", 1)[-1]


def add_dated_item(page: Page, description: str, when: datetime) -> None:
    """The due date is optional by default, so the form hides it until asked."""
    page.get_by_role("button", name="Add item").click()
    page.get_by_role("button", name="More fields").click()
    page.get_by_role("textbox", name="Description").fill(description)
    page.get_by_role("textbox", name="Due").fill(when.strftime("%Y-%m-%dT%H:%M"))
    page.get_by_role("button", name="Save").click()
    expect(page.get_by_text(description)).to_be_visible()


@pytest.mark.dashboard
def test_signing_in_lands_on_the_dashboard(
    page: Page, base_url: str, demo_member: DemoMember
) -> None:
    """R44: the surface that answers "what do I need to do" is what you open to."""
    ui_sign_in(page, base_url, demo_member)

    expect(page).to_have_url(f"{base_url}/dashboard/day")
    expect(page.get_by_role("heading", name="Agenda")).to_be_visible()
    expect(page.get_by_role("tab", name="Day")).to_have_attribute("aria-selected", "true")


@pytest.mark.dashboard
def test_an_item_due_today_appears_on_the_agenda_and_in_the_week(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_lists
) -> None:
    name = test_data.name("Planning")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    create_list(page, name)

    # Late enough today that the local day has not rolled over mid-test.
    later_today = datetime.now().replace(hour=23, minute=0, second=0, microsecond=0)
    add_dated_item(page, "Due today", later_today)

    page.get_by_role("link", name="Agenda").click()
    main = page.get_by_role("main")
    expect(main.get_by_text("Due today")).to_be_visible()
    # Scoped: the sidebar's Recent section names the list too.
    expect(main.get_by_text(name).first).to_be_visible()

    page.get_by_role("tab", name="Week").click()
    expect(page.get_by_role("button", name="Next week")).to_be_visible()
    expect(page.get_by_text("Due today")).to_be_visible()

    page.get_by_role("tab", name="Month").click()
    expect(page.get_by_text("Due today")).to_be_visible()


@pytest.mark.dashboard
def test_an_undated_item_shows_beside_every_range(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_lists
) -> None:
    """R45: a planning view that quietly dropped these would hide most of the work."""
    name = test_data.name("Undated")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    create_list(page, name)

    page.get_by_role("button", name="Add item").click()
    page.get_by_role("textbox", name="Description").fill("No date on this")
    page.get_by_role("button", name="Save").click()
    expect(page.get_by_text("No date on this")).to_be_visible()

    page.goto(f"{base_url}/dashboard/day")
    panel = page.get_by_role("heading", name="No date yet").locator("xpath=..")
    expect(panel.get_by_text("No date on this")).to_be_visible()

    page.get_by_role("tab", name="Week").click()
    expect(panel.get_by_text("No date on this")).to_be_visible()

    page.get_by_role("tab", name="Month").click()
    expect(panel.get_by_text("No date on this")).to_be_visible()


@pytest.mark.dashboard
def test_a_private_list_never_reaches_another_members_dashboard(
    page: Page,
    second_member_page: Page,
    base_url: str,
    demo_member: DemoMember,
    passwordless_member: DemoMember,
    test_data: TestData,
    cleanup_lists,
) -> None:
    """R19 across lists, which is what this surface risks getting wrong.

    Two contexts, because the leak this guards against is only visible from the
    other member's session.
    """
    # Labelled for this module: test_data namespaces runs, not labels, so two
    # modules reaching for name("Private") collide on "You already have a list
    # with that name."
    private_name = test_data.name("DashPrivate")
    shared_name = test_data.name("DashShared")
    cleanup_lists(private_name)
    cleanup_lists(shared_name)
    ui_sign_in(page, base_url, demo_member)

    create_list(page, private_name)
    later_today = datetime.now().replace(hour=23, minute=0, second=0, microsecond=0)
    add_dated_item(page, "Ana's secret", later_today)

    page.get_by_role("link", name="Agenda").click()
    create_list(page, shared_name, share_with=passwordless_member.display_name)
    add_dated_item(page, "Everybody's chore", later_today)

    # Ben's dashboard holds the shared one and nothing else, in every view.
    ui_sign_in(second_member_page, base_url, passwordless_member)
    for tab in ("Day", "Week", "Month"):
        second_member_page.get_by_role("tab", name=tab).click()
        expect(second_member_page.get_by_text("Everybody's chore")).to_be_visible()
        expect(second_member_page.get_by_text("Ana's secret")).to_have_count(0)


@pytest.mark.dashboard
def test_opening_an_item_is_a_url_the_back_button_closes(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_lists
) -> None:
    name = test_data.name("Popup")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    create_list(page, name)
    tomorrow = (datetime.now() + timedelta(days=1)).replace(hour=9, minute=0, second=0, microsecond=0)
    add_dated_item(page, "Tap me", tomorrow)

    page.goto(f"{base_url}/dashboard/week")
    page.get_by_role("button", name=f"Tap me, on {name}").click()

    dialog = page.get_by_role("dialog")
    expect(dialog).to_be_visible()
    # exact, because the dialog's own "Open <name>" button contains the name.
    expect(dialog.get_by_text(name, exact=True)).to_be_visible()

    page.go_back()
    expect(page.get_by_role("dialog")).to_have_count(0)
    # And the view behind it is still there.
    expect(page.get_by_role("button", name="Next week")).to_be_visible()


@pytest.mark.dashboard
def test_mine_narrows_the_dashboard_without_hiding_it_from_the_url(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_lists
) -> None:
    name = test_data.name("Scoped")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    list_id = create_list(page, name)

    page.goto(f"{base_url}/lists/{list_id}/settings")
    page.get_by_role("switch", name="Assignee").click()
    page.get_by_role("button", name="Save changes").click()
    expect(page.get_by_role("heading", name=name)).to_be_visible()

    later_today = datetime.now().replace(hour=23, minute=0, second=0, microsecond=0)
    page.get_by_role("button", name="Add item").click()
    page.get_by_role("button", name="More fields").click()
    page.get_by_role("textbox", name="Description").fill("Nobody's job")
    page.get_by_role("textbox", name="Due").fill(later_today.strftime("%Y-%m-%dT%H:%M"))
    page.get_by_role("button", name="Save").click()
    expect(page.get_by_text("Nobody's job")).to_be_visible()

    page.goto(f"{base_url}/dashboard/day")
    # The default surfaces unclaimed work, which is the point of it.
    expect(page.get_by_text("Nobody's job")).to_be_visible()

    page.get_by_role("button", name="Mine").click()

    expect(page).to_have_url(f"{base_url}/dashboard/day?scope=mine")
    expect(page.get_by_text("Nobody's job")).to_have_count(0)


@pytest.mark.dashboard
def test_a_member_walks_to_another_day_and_back(
    page: Page, base_url: str, demo_member: DemoMember
) -> None:
    """
    Stepping, and getting home again (requirements/0008 R74, R75).

    The return control is the assertion that matters: it is disabled on the
    current period, so a member can always tell whether they are looking at now
    without reading the date.
    """
    ui_sign_in(page, base_url, demo_member)
    page.goto(f"{base_url}/dashboard/day")

    expect(page.get_by_role("button", name="Today", exact=True)).to_be_disabled()
    heading = page.get_by_role("heading", name="Today", exact=True)
    expect(heading).to_be_visible()

    page.get_by_role("button", name="Next day").click()

    expect(page.get_by_role("heading", name="Today", exact=True)).to_have_count(0)
    expect(page.get_by_role("button", name="Today", exact=True)).to_be_enabled()
    expect(page).to_have_url(re.compile(r"[?&]on=\d{4}-\d{2}-\d{2}"))

    page.get_by_role("button", name="Previous day").click()
    expect(page.get_by_role("heading", name="Today", exact=True)).to_be_visible()
    expect(page.get_by_role("button", name="Today", exact=True)).to_be_disabled()


@pytest.mark.dashboard
def test_a_day_is_a_link_somebody_else_can_follow(
    page: Page, base_url: str, demo_member: DemoMember
) -> None:
    """R76: the period is in the address, so it survives a reload and a share."""
    ui_sign_in(page, base_url, demo_member)

    page.goto(f"{base_url}/dashboard/day?on=2026-02-11")
    expect(page.get_by_role("heading", name=re.compile(r"(11 February|February 11)"))).to_be_visible()

    page.reload()
    expect(page.get_by_role("heading", name=re.compile(r"(11 February|February 11)"))).to_be_visible()

    # And the range change keeps it rather than jumping home.
    page.get_by_role("tab", name="Month").click()
    expect(page.get_by_role("heading", name=re.compile(r"February 2026"))).to_be_visible()


@pytest.mark.dashboard
def test_a_mangled_address_still_opens_the_dashboard(
    page: Page, base_url: str, demo_member: DemoMember
) -> None:
    """
    This is the landing page. A truncated or edited ?on= must not be a member
    who cannot get into the product at all.
    """
    ui_sign_in(page, base_url, demo_member)

    page.goto(f"{base_url}/dashboard/day?on=not-a-date")

    expect(page.get_by_role("heading", name="Today", exact=True)).to_be_visible()


@pytest.mark.dashboard
def test_stepping_past_what_has_been_read_says_so(
    page: Page, base_url: str, demo_member: DemoMember
) -> None:
    """
    R77. An empty month a long way out means "nobody looked", not "nothing is
    on", and only the product knows which.
    """
    ui_sign_in(page, base_url, demo_member)
    page.goto(f"{base_url}/dashboard/month?on=2030-04-15")

    expect(page.get_by_text("No calendar events have been read")).to_be_visible()
