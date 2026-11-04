"""
Who can see a list (requirements/0004 R19).

Two browser contexts, which is what this suite exists for: an access grant is
exactly the kind of rule that looks right in one session and is wrong across
two, and nothing until now had used second_member_page for its stated purpose.
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


@pytest.mark.access
def test_a_list_shared_with_nobody_is_invisible_to_everyone_else(
    page: Page,
    second_member_page: Page,
    base_url: str,
    demo_member: DemoMember,
    passwordless_member: DemoMember,
    test_data: TestData,
    cleanup_lists,
) -> None:
    name = test_data.name("Private")
    cleanup_lists(name)

    ui_sign_in(page, base_url, demo_member)
    list_id = create_list(page, name)

    ui_sign_in(second_member_page, base_url, passwordless_member)
    results = second_member_page.get_by_role("main")
    results.get_by_role("textbox", name="Search lists").fill(name)
    expect(results.get_by_text("No lists match that.")).to_be_visible()

    # Not reachable by its id either: R19 hides that the list exists at all,
    # which is why this is the same answer a deleted list gives.
    second_member_page.goto(f"{base_url}/lists/{list_id}")
    expect(
        second_member_page.get_by_text("no longer exists, or was never shared")
    ).to_be_visible()


@pytest.mark.access
def test_sharing_a_list_makes_it_appear_and_revoking_takes_it_away(
    page: Page,
    second_member_page: Page,
    base_url: str,
    demo_member: DemoMember,
    passwordless_member: DemoMember,
    test_data: TestData,
    cleanup_lists,
) -> None:
    name = test_data.name("Shared")
    cleanup_lists(name)

    ui_sign_in(page, base_url, demo_member)
    list_id = create_list(page, name, share_with=passwordless_member.display_name)

    ui_sign_in(second_member_page, base_url, passwordless_member)
    results = second_member_page.get_by_role("main")
    results.get_by_role("textbox", name="Search lists").fill(name)
    expect(results.get_by_text(name)).to_be_visible()

    # A grantee is a peer on the list, but not its owner.
    second_member_page.goto(f"{base_url}/lists/{list_id}")
    expect(second_member_page.get_by_role("heading", name=name)).to_be_visible()
    expect(second_member_page.get_by_role("link", name="Settings")).to_have_count(0)

    # Now take it away.
    page.goto(f"{base_url}/lists/{list_id}/settings")
    # Unticking is how access is taken away - the same control that granted it.
    page.get_by_role("checkbox", name=passwordless_member.display_name).click()
    page.get_by_role("button", name="Save changes").click()
    expect(page.get_by_role("heading", name=name)).to_be_visible()

    second_member_page.reload()
    expect(
        second_member_page.get_by_text("no longer exists, or was never shared")
    ).to_be_visible()


@pytest.mark.access
def test_a_grantee_cannot_reach_the_settings_screen_even_by_its_url(
    page: Page,
    second_member_page: Page,
    base_url: str,
    demo_member: DemoMember,
    passwordless_member: DemoMember,
    test_data: TestData,
    cleanup_lists,
) -> None:
    name = test_data.name("Peer")
    cleanup_lists(name)

    ui_sign_in(page, base_url, demo_member)
    list_id = create_list(page, name, share_with=passwordless_member.display_name)

    ui_sign_in(second_member_page, base_url, passwordless_member)
    second_member_page.goto(f"{base_url}/lists/{list_id}/settings")

    expect(
        second_member_page.get_by_text("Only the person who created this list can change it")
    ).to_be_visible()
