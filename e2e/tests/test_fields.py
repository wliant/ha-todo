"""
What shape a list says its items take (requirements/0004 R21-R23).

The configuration is the thing items will be built against, so what matters
here is that it survives a reload exactly as it was set - including the ids
the server merges on.
"""

from __future__ import annotations

import re
from datetime import datetime

import pytest
from playwright.sync_api import Page, expect

from conftest import DemoMember, TestData, fill_due, ui_sign_in


def create_list(page: Page, name: str) -> str:
    # "New list" lives on the list of lists, and signing in no longer lands
    # there - the dashboard does (R44). The sidebar is always present.
    page.get_by_role("link", name="Lists").first.click()
    page.get_by_role("link", name="New list").click()
    page.get_by_role("textbox", name="Name", exact=True).fill(name)
    page.get_by_role("button", name="Create list").click()
    expect(page.get_by_role("heading", name=name)).to_be_visible()
    return page.url.rsplit("/", 1)[-1]


@pytest.mark.fields
def test_a_list_declares_two_costs_and_remembers_them(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_lists
) -> None:
    name = test_data.name("Chores")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    list_id = create_list(page, name)

    page.goto(f"{base_url}/lists/{list_id}/settings")
    page.get_by_role("switch", name="Assignee").click()
    page.get_by_role("checkbox", name="Assignee is required").click()
    # No switch for the due date any more: every list declares it (R40).

    # A chore costs both time and money - different questions, different units.
    page.get_by_role("button", name="Add a cost").click()
    page.get_by_role("textbox", name="Cost name").fill("Time")
    points = page.get_by_role("spinbutton", name="Points")
    points.nth(0).fill("10")
    points.nth(1).fill("50")
    points.nth(2).fill("120")

    page.get_by_role("button", name="Add a cost").click()
    money = page.get_by_role("textbox", name="Cost name").nth(1)
    money.fill("Money")
    page.get_by_role("button", name="A number").nth(1).click()
    page.get_by_role("textbox", name="Measured in").fill("dollar")

    page.get_by_role("button", name="Save changes").click()
    expect(page.get_by_role("heading", name=name)).to_be_visible()

    # The configuration is not restated on the list itself - what matters is
    # that it comes back the same way, and that the item form asks for it.

    page.goto(f"{base_url}/lists/{list_id}/settings")
    expect(page.get_by_role("switch", name="Assignee")).to_be_checked()
    expect(page.get_by_role("checkbox", name="Assignee is required")).to_be_checked()
    names = page.get_by_role("textbox", name="Cost name")
    expect(names.nth(0)).to_have_value("Time")
    expect(names.nth(1)).to_have_value("Money")
    expect(page.get_by_role("textbox", name="Measured in")).to_have_value("dollar")
    expect(page.get_by_role("spinbutton", name="Points").nth(2)).to_have_value("120")


@pytest.mark.fields
def test_a_fixed_cost_with_no_choices_is_refused_before_it_is_sent(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_lists
) -> None:
    name = test_data.name("Unsaveable")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    list_id = create_list(page, name)

    page.goto(f"{base_url}/lists/{list_id}/settings")
    page.get_by_role("button", name="Add a cost").click()
    page.get_by_role("textbox", name="Cost name").fill("Time")
    for _ in range(3):
        page.get_by_role("button", name="Remove choice").first.click()

    expect(page.get_by_text("needs at least one choice")).to_be_visible()
    expect(page.get_by_role("button", name="Save changes")).to_be_disabled()


@pytest.mark.fields
def test_renaming_a_choice_keeps_the_rest_of_the_cost_intact(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_lists
) -> None:
    """
    The server merges on ids rather than recreating rows, which is what stops
    item values being orphaned once items exist.
    """
    name = test_data.name("Renamed choices")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    list_id = create_list(page, name)

    page.goto(f"{base_url}/lists/{list_id}/settings")
    page.get_by_role("button", name="Add a cost").click()
    page.get_by_role("textbox", name="Cost name").fill("Time")
    page.get_by_role("button", name="Save changes").click()
    expect(page.get_by_role("heading", name=name)).to_be_visible()

    page.goto(f"{base_url}/lists/{list_id}/settings")
    page.get_by_role("textbox", name="Choice").first.fill("Five minutes")
    page.get_by_role("button", name="Save changes").click()
    expect(page.get_by_role("heading", name=name)).to_be_visible()

    page.goto(f"{base_url}/lists/{list_id}/settings")
    choices = page.get_by_role("textbox", name="Choice")
    expect(choices.nth(0)).to_have_value("Five minutes")
    expect(choices.nth(1)).to_have_value("30 min")
    expect(choices).to_have_count(3)


@pytest.mark.fields
def test_a_brand_new_list_already_offers_a_due_date(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_lists
) -> None:
    """R40: every list declares a due date, and nobody has to switch it on.

    The dashboard reads dates across every list the member can see, so a list
    that could omit the field would be silently missing from it (adr/0022).
    Asserted from the member's side: the input is simply there on a list where
    nothing was configured at all.
    """
    name = test_data.name("Dated")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    list_id = create_list(page, name)

    # Nothing configured, straight to adding an item. The due date is optional
    # by default, so it waits behind the form's toggle (R39).
    page.get_by_role("button", name="Add item").click()
    page.get_by_role("button", name="More fields").click()
    page.get_by_role("textbox", name="Description").fill("Pay the rent")
    fill_due(page, datetime(2026, 12, 1, 9, 0))
    page.get_by_role("button", name="Save").click()

    expect(page.get_by_text("Pay the rent")).to_be_visible()
    # Matched loosely: the chip is formatted by Intl in the browser's locale,
    # so the month's spelling is not ours to pin down.
    expect(page.get_by_text(re.compile(r"Due\b.*Dec.*1"))).to_be_visible()

    # And it cannot be switched off, only insisted upon.
    page.goto(f"{base_url}/lists/{list_id}/settings")
    due_date = page.get_by_role("switch", name="Due date and time")
    expect(due_date).to_be_checked()
    expect(due_date).to_be_disabled()

@pytest.mark.fields
def test_a_due_date_is_two_taps_and_defaults_to_the_end_of_the_day(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_lists
) -> None:
    """
    0012 R94, R95, R97: Today and Tomorrow in one tap, 23:59 supplied, the date
    steppable a day at a time, and clearable.
    """
    name = test_data.name("QuickDue")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    create_list(page, name)

    page.get_by_role("button", name="Add item").click()
    page.get_by_role("button", name="More fields").click()
    page.get_by_role("textbox", name="Description").fill("Bins out")

    date = page.get_by_label(re.compile(r"^Date"))
    time = page.get_by_label(re.compile(r"^Time"))

    # Nothing set, so there is no date to step - and the control says so rather
    # than inventing a starting point.
    expect(date).to_have_value("")
    expect(page.get_by_role("button", name="A day later")).to_be_disabled()

    page.get_by_role("button", name="Today", exact=True).click()
    today = date.input_value()
    assert today != ""
    # R95. Midnight would put the chore on the day before the one it means.
    expect(time).to_have_value("23:59")

    page.get_by_role("button", name="Tomorrow", exact=True).click()
    tomorrow = date.input_value()
    assert tomorrow > today

    # And a day either side of wherever it is.
    page.get_by_role("button", name="A day earlier").click()
    expect(date).to_have_value(today)
    page.get_by_role("button", name="A day later").click()
    expect(date).to_have_value(tomorrow)

    # R97: optional here, so it can be taken off without emptying the form.
    page.get_by_role("button", name="Clear", exact=True).click()
    expect(date).to_have_value("")

    page.get_by_role("button", name="Tomorrow", exact=True).click()
    page.get_by_role("button", name="Save").click()

    expect(page.get_by_text("Bins out")).to_be_visible()
    # Anchored: the list's own name ends in "Due" and would otherwise match.
    # The time rides along, which is R95 read back off the saved item.
    expect(page.get_by_text(re.compile(r"^Due\b.*(11:59|23:59)"))).to_be_visible()
