"""
Lists: making them, finding them, pinning them (requirements/0004 R18, R26-R28).

Who can see one is test_access.py's subject, because that needs two browser
contexts and is the reason this suite exists.
"""

from __future__ import annotations

import pytest
from playwright.sync_api import Page, expect

from conftest import DemoMember, TestData, ui_sign_in


@pytest.mark.lists
def test_member_creates_a_list_and_then_finds_it_by_name(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_lists
) -> None:
    name = test_data.name("Garden")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)

    page.get_by_role("link", name="New list").click()
    page.get_by_role("textbox", name="Name", exact=True).fill(name)
    page.get_by_role("textbox", name="Description").fill("Jobs outside")
    page.get_by_role("button", name="Garden", exact=True).click()
    page.get_by_role("button", name="Create list").click()

    # Lands on the new list.
    expect(page.get_by_role("heading", name=name)).to_be_visible()
    expect(page.get_by_text("Jobs outside")).to_be_visible()

    # And it is findable among everything else. Scoped to the main region: the
    # sidebar lists it under Recent too, and whether that has refreshed yet is
    # not what this test is about.
    page.get_by_role("link", name="Lists").first.click()
    results = page.get_by_role("main")
    results.get_by_role("textbox", name="Search lists").fill(name)
    expect(results.get_by_text(name)).to_be_visible()

    results.get_by_role("textbox", name="Search lists").fill("something-else-entirely")
    expect(results.get_by_text("No lists match that.")).to_be_visible()


@pytest.mark.lists
def test_pinning_a_list_puts_it_in_the_sidebar(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_lists
) -> None:
    name = test_data.name("Pinned")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)

    page.get_by_role("link", name="New list").click()
    page.get_by_role("textbox", name="Name", exact=True).fill(name)
    page.get_by_role("button", name="Create list").click()
    expect(page.get_by_role("heading", name=name)).to_be_visible()

    page.get_by_role("button", name=f"Pin {name}").click()

    sidebar = page.get_by_text("Pinned", exact=True)
    expect(sidebar).to_be_visible()
    expect(page.get_by_role("link", name=name)).to_be_visible()

    page.get_by_role("button", name=f"Unpin {name}").click()
    expect(page.get_by_text("Pinned", exact=True)).not_to_be_visible()


@pytest.mark.lists
def test_a_list_can_be_renamed_and_deleted_by_its_owner(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_lists
) -> None:
    name = test_data.name("Renamed")
    renamed = f"{name} again"
    cleanup_lists(name)
    cleanup_lists(renamed)
    ui_sign_in(page, base_url, demo_member)

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
