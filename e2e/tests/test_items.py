"""
Items (requirements/0004 R29, R30) and the points they earn (R23).

Two of these use a second browser context, which is what this suite is for:
an item two people can reach is exactly the thing that looks right in one
session and is wrong across two.
"""

from __future__ import annotations

import pytest
from playwright.sync_api import Page, expect

from conftest import DemoMember, TestData, ui_sign_in


def create_list(page: Page, name: str, share_with: str | None = None) -> str:
    page.get_by_role("link", name="New list").click()
    page.get_by_role("textbox", name="Name", exact=True).fill(name)
    if share_with:
        page.get_by_role("checkbox", name=share_with).click()
    page.get_by_role("button", name="Create list").click()
    expect(page.get_by_role("heading", name=name)).to_be_visible()
    return page.url.rsplit("/", 1)[-1]


def add_item(page: Page, description: str) -> None:
    page.get_by_role("button", name="Add item").click()
    page.get_by_role("textbox", name="Description").fill(description)
    page.get_by_role("button", name="Save").click()
    expect(page.get_by_text(description)).to_be_visible()


@pytest.mark.items
def test_a_member_adds_an_item_and_ticks_it_off(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_lists
) -> None:
    name = test_data.name("Doing")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    create_list(page, name)

    add_item(page, "Buy milk")
    # click, not check: a completed item leaves the "To do" view, so the
    # checkbox is gone by the time check() would assert its new state.
    page.get_by_role("checkbox", name="Complete Buy milk").click()

    # "To do" is the default view, so a finished item leaves it.
    expect(page.get_by_text("Buy milk")).not_to_be_visible()
    page.get_by_role("button", name="Done").click()
    expect(page.get_by_text("Buy milk")).to_be_visible()


@pytest.mark.items
def test_points_go_to_whoever_completed_it_not_the_assignee(
    page: Page,
    second_member_page: Page,
    base_url: str,
    demo_member: DemoMember,
    passwordless_member: DemoMember,
    test_data: TestData,
    cleanup_lists,
) -> None:
    """
    The rule a refactor is most likely to invert, and only observable here.
    """
    name = test_data.name("Scoring")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    list_id = create_list(page, name, share_with=passwordless_member.display_name)

    page.goto(f"{base_url}/lists/{list_id}/settings")
    page.get_by_role("switch", name="Assignee").click()
    page.get_by_role("button", name="Add a cost").click()
    page.get_by_role("textbox", name="Cost name").fill("Time")
    page.get_by_role("spinbutton", name="Points").nth(1).fill("50")
    page.get_by_role("button", name="Save changes").click()
    expect(page.get_by_role("heading", name=name)).to_be_visible()

    # Assigned to Ben...
    page.get_by_role("button", name="Add item").click()
    page.get_by_role("textbox", name="Description").fill("Sweep up")
    page.get_by_role("combobox", name="Assignee").click()
    page.get_by_role("option", name=passwordless_member.display_name).click()
    page.get_by_role("combobox", name="Time").click()
    page.get_by_role("option", name="30 min", exact=False).click()
    page.get_by_role("button", name="Save").click()
    expect(page.get_by_text("Sweep up")).to_be_visible()

    # ...and completed by Ana.
    page.get_by_role("checkbox", name="Complete Sweep up").click()

    points = page.get_by_role("heading", name="Points").locator("xpath=../..")
    expect(points.get_by_text(demo_member.display_name)).to_be_visible()
    expect(points.get_by_text("50")).to_be_visible()
    expect(points.get_by_text(passwordless_member.display_name)).to_have_count(0)


@pytest.mark.items
def test_the_second_member_to_tick_an_item_is_told_who_got_there_first(
    page: Page,
    second_member_page: Page,
    base_url: str,
    demo_member: DemoMember,
    passwordless_member: DemoMember,
    test_data: TestData,
    cleanup_lists,
) -> None:
    """
    R30: a concurrent completion is never silently discarded, and never
    silently overwrites. This is the scenario second_member_page exists for.
    """
    name = test_data.name("Contended")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    list_id = create_list(page, name, share_with=passwordless_member.display_name)
    add_item(page, "Take the bins out")

    # Both members are looking at the same open item.
    ui_sign_in(second_member_page, base_url, passwordless_member)
    second_member_page.goto(f"{base_url}/lists/{list_id}")
    expect(second_member_page.get_by_text("Take the bins out")).to_be_visible()

    # Ben gets there first.
    second_member_page.get_by_role("checkbox", name="Complete Take the bins out").click()
    expect(second_member_page.get_by_text("Take the bins out")).not_to_be_visible()

    # Ana ticks the item her screen still shows as open.
    page.get_by_role("checkbox", name="Complete Take the bins out").click()

    expect(page.get_by_text(f"{passwordless_member.display_name} completed this first")
           ).to_be_visible()
    # And her screen now agrees with the server rather than with her click.
    page.get_by_role("button", name="Done").click()
    expect(page.get_by_text("Take the bins out")).to_be_visible()


@pytest.mark.items
def test_an_item_cannot_be_saved_without_what_the_list_insists_on(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_lists
) -> None:
    name = test_data.name("Insisting")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    list_id = create_list(page, name)

    page.goto(f"{base_url}/lists/{list_id}/settings")
    page.get_by_role("switch", name="Due date and time").click()
    page.get_by_role("checkbox", name="Due date and time is required").click()
    page.get_by_role("button", name="Save changes").click()
    expect(page.get_by_role("heading", name=name)).to_be_visible()

    page.get_by_role("button", name="Add item").click()
    page.get_by_role("textbox", name="Description").fill("No date on this")
    page.get_by_role("button", name="Save").click()

    expect(page.get_by_text("needs a due date")).to_be_visible()
