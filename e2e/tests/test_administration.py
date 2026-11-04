"""
The admin adding and removing household members, through the real UI.

Needs E2E_ADMIN_PASSWORD - the admin credential is environment configuration,
not seed data, so these tests skip rather than guess it.
"""

from __future__ import annotations

import httpx
import pytest
from playwright.sync_api import Page, expect

from conftest import DemoMember, TestData, api_csrf_header, pick_user, ui_sign_in


def select_person(page: Page, display_name: str) -> None:
    """Pick someone out of the master list to open their detail pane."""
    page.get_by_role("button", name=display_name, exact=False).first.click()


def delete_via_api(admin_api: httpx.Client, username: str) -> None:
    """Teardown: remove the user if the test did not get that far itself."""
    users = admin_api.get("/api/admin/users")
    users.raise_for_status()
    for user in users.json():
        if user["username"] == username:
            admin_api.delete(
                f"/api/admin/users/{user['id']}", headers=api_csrf_header(admin_api)
            )


@pytest.mark.smoke
def test_admin_can_sign_in_and_reach_user_administration(
    page: Page, base_url: str, admin_member: DemoMember
) -> None:
    ui_sign_in(page, base_url, admin_member)

    expect(page.get_by_role("heading", name="Lists")).to_be_visible()
    page.get_by_role("link", name="Users").click()

    expect(page.get_by_role("heading", name="Users")).to_be_visible()
    # Master-detail (R16): the household is the master, and adding someone is
    # a detail pane rather than a form permanently occupying the screen.
    expect(page.get_by_role("heading", name="Add someone")).to_have_count(0)
    page.get_by_role("button", name="Add someone").click()
    expect(page.get_by_role("heading", name="Add someone")).to_be_visible()


def test_admin_is_in_the_sign_in_list_without_being_marked_as_the_admin(
    page: Page, base_url: str, admin_member: DemoMember
) -> None:
    page.goto(f"{base_url}/login")

    admin_choice = page.get_by_role("button", name=admin_member.display_name)
    # A name, like anybody else: the row must not say who administers the
    # household (R17).
    expect(admin_choice).to_have_text(admin_member.display_name)

    # The password is asked for only after picking, once the server refuses
    # the attempt made without one.
    admin_choice.click()
    expect(page.get_by_label("Password")).to_be_visible()


def test_admin_cannot_remove_their_own_account(
    page: Page, base_url: str, admin_member: DemoMember
) -> None:
    ui_sign_in(page, base_url, admin_member)
    page.goto(f"{base_url}/admin")

    expect(page.get_by_role("heading", name="Users")).to_be_visible()
    select_person(page, admin_member.display_name)

    expect(page.get_by_text("cannot be removed")).to_be_visible()
    expect(
        page.get_by_role("button", name=f"Remove {admin_member.display_name}")
    ).to_have_count(0)


def test_added_passwordless_member_appears_and_can_sign_in(
    page: Page,
    second_member_page: Page,
    base_url: str,
    admin_member: DemoMember,
    admin_api: httpx.Client,
    test_data: TestData,
) -> None:
    """The admin adds someone; that person signs in by being picked, no password."""
    username = test_data.username("picked")
    display_name = test_data.name("Picked")
    test_data.defer_cleanup(lambda: delete_via_api(admin_api, username))

    ui_sign_in(page, base_url, admin_member)
    page.goto(f"{base_url}/admin")
    page.get_by_role("button", name="Add someone").click()
    page.get_by_role("textbox", name="Name", exact=True).fill(display_name)
    page.get_by_role("textbox", name="Login name").fill(username)
    page.get_by_role("button", name="Add user").click()

    expect(page.get_by_text(username).first).to_be_visible()

    # A second browser, i.e. that family member on their own device.
    new_member = DemoMember(username=username, display_name=display_name, password=None)
    second_member_page.goto(f"{base_url}/login")
    choice = second_member_page.get_by_role("button", name=display_name)
    expect(choice).to_have_text(display_name)
    pick_user(second_member_page, new_member)

    expect(second_member_page.get_by_role("heading", name="Lists")).to_be_visible()
    expect(second_member_page.get_by_text(display_name).first).to_be_visible()


def test_added_member_with_a_password_must_type_it(
    page: Page,
    second_member_page: Page,
    base_url: str,
    admin_member: DemoMember,
    admin_api: httpx.Client,
    test_data: TestData,
) -> None:
    username = test_data.username("secret")
    display_name = test_data.name("Secret")
    password = "e2e-password-" + username[-8:]
    test_data.defer_cleanup(lambda: delete_via_api(admin_api, username))

    ui_sign_in(page, base_url, admin_member)
    page.goto(f"{base_url}/admin")
    page.get_by_role("button", name="Add someone").click()
    page.get_by_role("textbox", name="Name", exact=True).fill(display_name)
    page.get_by_role("textbox", name="Login name").fill(username)
    page.get_by_label("Require a password").check()
    page.get_by_role("textbox", name="Password", exact=True).fill(password)
    page.get_by_role("button", name="Add user").click()

    expect(page.get_by_text(username).first).to_be_visible()

    second_member_page.goto(f"{base_url}/login")
    choice = second_member_page.get_by_role("button", name=display_name)
    expect(choice).to_have_text(display_name)
    choice.click()

    second_member_page.get_by_label("Password").fill("wrong")
    second_member_page.get_by_role("button", name="Sign in", exact=True).click()
    expect(second_member_page.get_by_role("alert")).to_contain_text("did not work")

    second_member_page.get_by_label("Password").fill(password)
    second_member_page.get_by_role("button", name="Sign in", exact=True).click()
    expect(second_member_page.get_by_role("heading", name="Lists")).to_be_visible()


def test_removed_member_disappears_from_the_sign_in_screen(
    page: Page,
    second_member_page: Page,
    base_url: str,
    admin_member: DemoMember,
    admin_api: httpx.Client,
    test_data: TestData,
) -> None:
    username = test_data.username("gone")
    display_name = test_data.name("Gone")
    test_data.defer_cleanup(lambda: delete_via_api(admin_api, username))

    ui_sign_in(page, base_url, admin_member)
    page.goto(f"{base_url}/admin")
    page.get_by_role("button", name="Add someone").click()
    page.get_by_role("textbox", name="Name", exact=True).fill(display_name)
    page.get_by_role("textbox", name="Login name").fill(username)
    page.get_by_role("button", name="Add user").click()
    expect(page.get_by_text(username).first).to_be_visible()

    select_person(page, display_name)
    page.get_by_role("button", name=f"Remove {display_name}").click()
    expect(page.get_by_text(username)).to_have_count(0)

    second_member_page.goto(f"{base_url}/login")
    expect(second_member_page.get_by_role("button", name=display_name)).to_have_count(0)


def test_duplicate_login_name_is_refused_with_an_explanation(
    page: Page,
    base_url: str,
    admin_member: DemoMember,
    demo_member: DemoMember,
    test_data: TestData,
) -> None:
    ui_sign_in(page, base_url, admin_member)
    page.goto(f"{base_url}/admin")
    page.get_by_role("button", name="Add someone").click()

    page.get_by_role("textbox", name="Name", exact=True).fill(test_data.name("Clash"))
    page.get_by_role("textbox", name="Login name").fill(demo_member.username)
    page.get_by_role("button", name="Add user").click()

    expect(page.get_by_role("alert")).to_contain_text("already taken")
