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

from conftest import DemoMember, TestData, fill_due, ui_sign_in


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
    fill_due(page, when)
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

    # Anchored on the day the chore is due rather than on the current week.
    # Without ?on=, this passes six days out of seven and fails on a Sunday,
    # when "tomorrow" is the Monday of the *next* week and so is not in the
    # grid at all (weeks start Monday). The popup is the subject here, not which
    # week it happens to be.
    page.goto(f"{base_url}/dashboard/week?on={tomorrow:%Y-%m-%d}")
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
    fill_due(page, later_today)
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


@pytest.mark.dashboard
def test_a_member_completes_a_chore_from_the_agenda(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_lists
) -> None:
    """
    0010 R83, R86: the same write as doing it on the list, from the screen the
    member is already standing on - and the row then leaves the agenda.
    """
    name = test_data.name("Ticking")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    list_id = create_list(page, name)

    # Named for the run: the agenda crosses every list this member can see, so
    # a bare "Put the bins out" collides with the one another test left.
    chore = test_data.name("Put the bins out")
    later_today = datetime.now().replace(hour=23, minute=0, second=0, microsecond=0)
    add_dated_item(page, chore, later_today)

    page.get_by_role("link", name="Agenda").click()
    main = page.get_by_role("main")
    expect(main.get_by_text(chore)).to_be_visible()

    page.get_by_role("button", name=f"Complete {chore}", exact=True).click()

    # The agenda shows outstanding work, so it goes - and says so, because the
    # row vanishing cannot be the only feedback.
    expect(page.get_by_text(f"{chore} is done.")).to_be_visible()
    # Exact: the snackbar's own message contains the description too.
    expect(main.get_by_text(chore, exact=True)).not_to_be_visible()

    # The assertion no unit test can make: it is the *same* write. The list
    # shows it finished, by whoever actually did it (0005 R38).
    page.goto(f"{base_url}/lists/{list_id}")
    page.get_by_role("button", name="Done").click()
    expect(page.get_by_text(chore)).to_be_visible()
    # The completion chip, which the points panel's own mention of her name
    # cannot be mistaken for.
    expect(
        page.get_by_text(
            re.compile(
                rf"(Done by {demo_member.display_name}"
                rf"|{demo_member.display_name} \u2014 \d+ points)"
            )
        ).first
    ).to_be_visible()


@pytest.mark.dashboard
def test_a_chore_due_later_is_completed_from_the_month_view(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_lists
) -> None:
    """
    0010 R85: the popup is what makes the week and month views able to complete
    anything, so it is worth one test of its own.
    """
    name = test_data.name("Later")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    create_list(page, name)

    # Tomorrow, so the day view is not where it is found.
    tomorrow = (datetime.now() + timedelta(days=1)).replace(
        hour=9, minute=0, second=0, microsecond=0
    )
    chore = test_data.name("Pay the bill")
    add_dated_item(page, chore, tomorrow)

    page.get_by_role("link", name="Agenda").click()
    page.get_by_role("tab", name="Month").click()
    page.get_by_role("button", name=f"{chore}, on {name}").click()

    dialog = page.get_by_role("dialog")
    dialog.get_by_role("button", name=f"Complete {chore}", exact=True).click()

    # Its subject has stopped existing, so the popup closes with it.
    expect(dialog).not_to_be_visible()
    expect(page.get_by_text(f"{chore} is done.")).to_be_visible()
    expect(page.get_by_role("main").get_by_text(chore, exact=True)).not_to_be_visible()


@pytest.mark.dashboard
def test_the_second_member_to_tick_a_chore_on_the_agenda_is_told(
    page: Page,
    second_member_page: Page,
    base_url: str,
    demo_member: DemoMember,
    passwordless_member: DemoMember,
    test_data: TestData,
    cleanup_lists,
) -> None:
    """
    0010 R87, pointed at the new surface. Two members looking at the household
    agenda and reaching for the same chore is what this product is about.
    """
    name = test_data.name("Raced")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    create_list(page, name, share_with=passwordless_member.display_name)

    later_today = datetime.now().replace(hour=23, minute=0, second=0, microsecond=0)
    chore = test_data.name("Wash up")
    add_dated_item(page, chore, later_today)

    page.get_by_role("link", name="Agenda").click()
    expect(page.get_by_role("main").get_by_text(chore)).to_be_visible()

    # Ben, on his own agenda, gets there first.
    ui_sign_in(second_member_page, base_url, passwordless_member)
    second_member_page.goto(f"{base_url}/dashboard/day")
    expect(second_member_page.get_by_role("main").get_by_text(chore)).to_be_visible()
    second_member_page.get_by_role("button", name=f"Complete {chore}", exact=True).click()
    expect(second_member_page.get_by_text(f"{chore} is done.")).to_be_visible()

    # Ana completes the chore her screen still shows as due.
    page.get_by_role("button", name=f"Complete {chore}", exact=True).click()

    expect(
        page.get_by_text(f"{passwordless_member.display_name} completed this first")
    ).to_be_visible()


@pytest.mark.dashboard
def test_a_chore_whose_day_has_gone_is_shown_on_every_range(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_lists
) -> None:
    """
    0011 R88, R89, R90: a missed chore used to exist only in the window of the
    day it was due, which nobody navigates to. It is now on the agenda wherever
    the member is standing, and it can be cleared from there.

    The count itself is not asserted: dev is a shared deployment and other
    members' late work is legitimately in it. What this test owns is its own
    chore.
    """
    name = test_data.name("Behind")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    create_list(page, name)

    two_days_ago = (datetime.now() - timedelta(days=2)).replace(
        hour=9, minute=0, second=0, microsecond=0
    )
    chore = test_data.name("Fix the shelf")
    add_dated_item(page, chore, two_days_ago)

    page.get_by_role("link", name="Agenda").click()
    strip = page.get_by_role("status").filter(has_text=re.compile("overdue"))
    expect(strip).to_be_visible()

    # R89: the same strip, unchanged, on a range that has nothing to do with
    # the day the chore was due.
    page.get_by_role("tab", name="Month").click()
    expect(strip).to_be_visible()

    strip.get_by_role("button", name="Show").click()
    expect(strip.get_by_text(chore, exact=True)).to_be_visible()
    # How late, in words - the thing the grid could never say. Scoped to this
    # run's own list: a shared deployment legitimately has other late work, and
    # some of it is also two days old.
    expect(
        strip.get_by_text(re.compile(rf"{re.escape(name)} · due 2 days ago"))
    ).to_be_visible()

    # R90: cleared from where it was found, with the list's own gesture.
    strip.get_by_role("button", name=f"Complete {chore}", exact=True).click()
    expect(page.get_by_text(f"{chore} is done.")).to_be_visible()
    expect(page.get_by_text(chore, exact=True)).not_to_be_visible()
