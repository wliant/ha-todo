"""
Lists: making them, finding them, pinning them (requirements/0004 R18, R26-R28,
and 0012 R98-R102 for the sidebar they appear in).

Who can see one is test_access.py's subject, because that needs two browser
contexts and is the reason this suite exists.
"""

from __future__ import annotations

import re

import pytest
from playwright.sync_api import Page, expect

from conftest import DemoMember, TestData, profile_link, ui_sign_in


@pytest.mark.lists
def test_member_creates_a_list_and_then_finds_it_by_name(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_lists
) -> None:
    name = test_data.name("Garden")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)

    # Signing in lands on the dashboard (R44), and "New list" is on the list of
    # lists - so get there first.
    page.get_by_role("link", name="Lists").first.click()
    page.get_by_role("link", name="New list").click()
    page.get_by_role("textbox", name="Name", exact=True).fill(name)
    page.get_by_role("textbox", name="Description").fill("Jobs outside")
    page.get_by_role("button", name="Garden", exact=True).click()
    page.get_by_role("button", name="Create list").click()

    # Lands on the new list.
    expect(page.get_by_role("heading", name=name)).to_be_visible()
    expect(page.get_by_text("Jobs outside")).to_be_visible()

    # And it is findable among everything else. Scoped to the main region: the
    # sidebar's Lists group names it too (0012 R98), and whether that has
    # refreshed yet is not what this test is about.
    page.get_by_role("link", name="Lists").first.click()
    results = page.get_by_role("main")
    results.get_by_role("textbox", name="Search lists").fill(name)
    expect(results.get_by_text(name)).to_be_visible()

    results.get_by_role("textbox", name="Search lists").fill("something-else-entirely")
    expect(results.get_by_text("No lists match that.")).to_be_visible()


@pytest.mark.lists
def test_pinning_a_list_moves_it_into_the_pinned_section(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_lists
) -> None:
    """
    0012 R98, R105. The sidebar reaches every list now, so the list being
    *there* is no longer what a pin changes - what it changes is which of the
    two sections holds it. They partition, so nothing is drawn twice.
    """
    name = test_data.name("Pinned")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)

    # Signing in lands on the dashboard (R44), and "New list" is on the list of
    # lists - so get there first.
    page.get_by_role("link", name="Lists").first.click()
    page.get_by_role("link", name="New list").click()
    page.get_by_role("textbox", name="Name", exact=True).fill(name)
    page.get_by_role("button", name="Create list").click()
    expect(page.get_by_role("heading", name=name)).to_be_visible()

    nav = page.get_by_role("navigation", name="Main")
    pinned = page.get_by_role("list", name="Pinned")

    # In the sidebar's group before anything is pinned or even opened twice.
    expect(nav.get_by_role("link", name=name)).to_be_visible()

    page.get_by_role("button", name=f"Pin {name}").click()

    expect(pinned.get_by_role("link", name=name)).to_be_visible()
    expect(nav.get_by_role("link", name=name)).to_have_count(0)

    page.get_by_role("button", name=f"Unpin {name}").click()

    expect(nav.get_by_role("link", name=name)).to_be_visible()
    expect(pinned.get_by_role("link", name=name)).to_have_count(0)


@pytest.mark.lists
def test_the_sidebar_groups_collapse_and_still_navigate(
    page: Page, base_url: str, demo_member: DemoMember
) -> None:
    """0012 R99: the label is a link, the chevron is a separate control."""
    ui_sign_in(page, base_url, demo_member)

    expect(page.get_by_role("button", name="Hide lists").first).to_be_visible()
    page.get_by_role("button", name="Hide lists").first.click()
    expect(page.get_by_role("button", name="Show lists").first).to_be_visible()

    # Collapsed, the group's own label still goes to the index.
    page.get_by_role("link", name="Lists").first.click()
    expect(page.get_by_role("heading", name="Lists")).to_be_visible()


@pytest.mark.lists
def test_the_sidebar_shows_as_many_lists_as_the_member_asks_for(
    page: Page, base_url: str, demo_member: DemoMember
) -> None:
    """
    0012 R102. Read through the control rather than by counting rows: how many
    lists this account happens to have is demo data, not something to assert.
    """
    ui_sign_in(page, base_url, demo_member)
    profile_link(page, demo_member).click()

    field = page.get_by_label(re.compile(r"^Lists shown in each group"))
    expect(field).to_have_value("10")
    field.fill("2")
    field.blur()

    page.reload()
    expect(page.get_by_label(re.compile(r"^Lists shown in each group"))).to_have_value("2")

    # Put it back, because this account is shared with every other test.
    restored = page.get_by_label(re.compile(r"^Lists shown in each group"))
    restored.fill("10")
    restored.blur()
    page.reload()
    expect(page.get_by_label(re.compile(r"^Lists shown in each group"))).to_have_value("10")


@pytest.mark.lists
def test_a_list_can_be_renamed_and_deleted_by_its_owner(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_lists
) -> None:
    name = test_data.name("Renamed")
    renamed = f"{name} again"
    cleanup_lists(name)
    cleanup_lists(renamed)
    ui_sign_in(page, base_url, demo_member)

    # Signing in lands on the dashboard (R44), and "New list" is on the list of
    # lists - so get there first.
    page.get_by_role("link", name="Lists").first.click()
    page.get_by_role("link", name="New list").click()
    page.get_by_role("textbox", name="Name", exact=True).fill(name)
    page.get_by_role("button", name="Create list").click()
    expect(page.get_by_role("heading", name=name)).to_be_visible()

    page.get_by_role("link", name="Settings").click()
    page.get_by_role("textbox", name="Name", exact=True).fill(renamed)
    page.get_by_role("button", name="Save changes").click()
    expect(page.get_by_role("heading", name=renamed)).to_be_visible()

    page.get_by_role("link", name="Settings").click()
    page.get_by_role("button", name=f"Delete {renamed}").click()
    page.get_by_role("button", name="Delete", exact=True).click()

    expect(page.get_by_role("heading", name="Lists")).to_be_visible()
    results = page.get_by_role("main")
    results.get_by_role("textbox", name="Search lists").fill(renamed)
    expect(results.get_by_text("No lists match that.")).to_be_visible()
