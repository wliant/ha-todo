"""
Recurring items (requirements/0005 R41, R42).

The job that creates these runs on a cron in a real deployment, so these tests
trigger it by hand through the per-list endpoint the UI exposes - which is also
the escape hatch a member reaches for when something did not appear.
"""

from __future__ import annotations

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


@pytest.mark.recurring
def test_a_daily_rule_puts_an_item_on_the_list_and_only_one(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_lists
) -> None:
    """R42, and the guard that makes a second run a no-op rather than a duplicate."""
    name = test_data.name("Standing")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    list_id = create_list(page, name)

    page.get_by_role("tab", name="Recurring").click()
    expect(page.get_by_text("Nothing recurs on this list yet.")).to_be_visible()

    page.get_by_role("button", name="Add a recurring item").click()
    page.get_by_role("textbox", name="What recurs").fill("Vitamins")
    page.get_by_role("combobox", name="How often").click()
    page.get_by_role("option", name="Every day").click()
    page.get_by_role("button", name="Save").click()

    expect(page.get_by_text("Vitamins")).to_be_visible()
    expect(page.get_by_text("every day")).to_be_visible()

    page.get_by_role("button", name="Run now").click()
    expect(page.get_by_text("Added 1.")).to_be_visible()

    # It is an ordinary item now.
    page.get_by_role("tab", name="Items").click()
    expect(page.get_by_text("Vitamins")).to_be_visible()
    expect(page.get_by_role("button", name="Complete Vitamins", exact=True)).to_be_visible()

    # And running again does not make a second one.
    page.get_by_role("tab", name="Recurring").click()
    page.get_by_role("button", name="Run now").click()
    expect(page.get_by_text("Nothing was due - everything is already on the list.")).to_be_visible()

    page.get_by_role("tab", name="Items").click()
    expect(page.get_by_text("Vitamins")).to_have_count(1)


@pytest.mark.recurring
def test_a_weekly_rule_needs_a_day_before_it_can_be_saved(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_lists
) -> None:
    name = test_data.name("Weekdays")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    list_id = create_list(page, name)

    page.goto(f"{base_url}/lists/{list_id}/recurring")
    page.get_by_role("button", name="Add a recurring item").click()
    page.get_by_role("textbox", name="What recurs").fill("Bins out")

    # Weekly is the default shape, and no day is chosen yet.
    expect(page.get_by_role("button", name="Save")).to_be_disabled()

    page.get_by_role("button", name="TUESDAY").click()
    expect(page.get_by_role("button", name="Save")).to_be_enabled()
    page.get_by_role("button", name="Save").click()

    expect(page.get_by_text("Bins out")).to_be_visible()
    expect(page.get_by_text("Tue", exact=True)).to_be_visible()


@pytest.mark.recurring
def test_only_the_owner_writes_the_standing_instructions(
    page: Page,
    second_member_page: Page,
    base_url: str,
    demo_member: DemoMember,
    passwordless_member: DemoMember,
    test_data: TestData,
    cleanup_lists,
) -> None:
    """R20's reading: a rule is as much the shape of a list as its fields.

    But anyone who can see the list can run them, because what a run produces
    is an item they could have typed by hand.
    """
    name = test_data.name("Owned")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    list_id = create_list(page, name, share_with=passwordless_member.display_name)

    page.goto(f"{base_url}/lists/{list_id}/recurring")
    page.get_by_role("button", name="Add a recurring item").click()
    page.get_by_role("textbox", name="What recurs").fill("Ana's instruction")
    page.get_by_role("combobox", name="How often").click()
    page.get_by_role("option", name="Every day").click()
    page.get_by_role("button", name="Save").click()
    expect(page.get_by_text("Ana's instruction")).to_be_visible()

    # Ben sees it, and can run it, but has nothing to write with.
    ui_sign_in(second_member_page, base_url, passwordless_member)
    second_member_page.goto(f"{base_url}/lists/{list_id}/recurring")

    expect(second_member_page.get_by_text("Ana's instruction")).to_be_visible()
    expect(
        second_member_page.get_by_role("button", name="Add a recurring item")
    ).to_have_count(0)
    expect(
        second_member_page.get_by_role("button", name="Edit Ana's instruction")
    ).to_have_count(0)

    second_member_page.get_by_role("button", name="Run now").click()
    expect(second_member_page.get_by_text("Added 1.")).to_be_visible()


@pytest.mark.recurring
def test_the_recurring_tab_is_its_own_url(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_lists
) -> None:
    name = test_data.name("Tabbed")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    list_id = create_list(page, name)

    page.goto(f"{base_url}/lists/{list_id}/recurring")

    expect(page.get_by_role("heading", name="Recurring")).to_be_visible()
    expect(page.get_by_role("tab", name="Recurring")).to_have_attribute(
        "aria-selected", "true"
    )
