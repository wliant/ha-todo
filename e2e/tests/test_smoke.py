"""Smoke checks: the environment is alive and a member can get in."""

from __future__ import annotations

import httpx
import pytest
from playwright.sync_api import Page, expect

from conftest import DemoMember, pick_user, ui_sign_in


@pytest.mark.smoke
def test_health_endpoint_reports_up(api: httpx.Client) -> None:
    response = api.get("/actuator/health")

    assert response.status_code == 200
    assert response.json()["status"] == "UP"


@pytest.mark.smoke
def test_login_page_lists_the_household_instead_of_a_form(
    page: Page, base_url: str, demo_member: DemoMember, passwordless_member: DemoMember
) -> None:
    page.goto(f"{base_url}/login")

    expect(page.get_by_role("heading", name="Who's signing in?")).to_be_visible()
    expect(page.get_by_role("button", name=demo_member.display_name)).to_be_visible()
    expect(page.get_by_role("button", name=passwordless_member.display_name)).to_be_visible()
    # Nothing is asked for until someone says who they are.
    expect(page.get_by_label("Password")).to_have_count(0)


@pytest.mark.smoke
def test_member_with_a_password_is_asked_for_it(
    page: Page, base_url: str, demo_member: DemoMember
) -> None:
    page.goto(f"{base_url}/login")
    pick_user(page, demo_member)

    expect(page.get_by_text("Signing in as")).to_be_visible()
    expect(page.get_by_label("Password")).to_be_visible()

    page.get_by_label("Password").fill(demo_member.password or "")
    page.get_by_role("button", name="Sign in", exact=True).click()

    expect(page.get_by_role("heading", name="Lists")).to_be_visible()
    expect(page.get_by_text("Signed in as")).to_be_visible()


@pytest.mark.smoke
def test_passwordless_member_signs_in_with_one_click(
    page: Page, base_url: str, passwordless_member: DemoMember
) -> None:
    """The whole point of R10: no password is asked for, and none is typed."""
    page.goto(f"{base_url}/login")
    pick_user(page, passwordless_member)

    expect(page.get_by_role("heading", name="Lists")).to_be_visible()
    expect(page.get_by_text(passwordless_member.display_name)).to_be_visible()


def test_wrong_password_is_refused_without_saying_why(
    page: Page, base_url: str, demo_member: DemoMember
) -> None:
    page.goto(f"{base_url}/login")
    pick_user(page, demo_member)
    page.get_by_label("Password").fill("definitely-not-the-password")
    page.get_by_role("button", name="Sign in", exact=True).click()

    expect(page.get_by_role("alert")).to_contain_text("did not match")
    expect(page.get_by_role("heading", name="Lists")).to_have_count(0)


def test_member_can_back_out_of_the_wrong_name(
    page: Page, base_url: str, demo_member: DemoMember, passwordless_member: DemoMember
) -> None:
    page.goto(f"{base_url}/login")
    pick_user(page, demo_member)

    page.get_by_role("button", name=f"Not {demo_member.display_name}?").click()

    expect(page.get_by_role("button", name=passwordless_member.display_name)).to_be_visible()


def test_unauthenticated_member_is_sent_to_login(page: Page, base_url: str) -> None:
    page.goto(f"{base_url}/lists")

    expect(page.get_by_role("heading", name="Who's signing in?")).to_be_visible()


def test_sessions_are_isolated_between_members(
    page: Page,
    second_member_page: Page,
    base_url: str,
    demo_member: DemoMember,
) -> None:
    """One member signing in must not sign the other in."""
    ui_sign_in(page, base_url, demo_member)
    expect(page.get_by_role("heading", name="Lists")).to_be_visible()

    second_member_page.goto(f"{base_url}/lists")
    expect(second_member_page.get_by_role("heading", name="Who's signing in?")).to_be_visible()


@pytest.mark.smoke
def test_sign_out_returns_to_login(
    page: Page, base_url: str, demo_member: DemoMember
) -> None:
    ui_sign_in(page, base_url, demo_member)
    expect(page.get_by_role("heading", name="Lists")).to_be_visible()

    page.get_by_role("button", name="Sign out").click()

    expect(page.get_by_role("heading", name="Who's signing in?")).to_be_visible()


def test_member_is_offered_no_route_into_administration(
    page: Page, base_url: str, demo_member: DemoMember
) -> None:
    ui_sign_in(page, base_url, demo_member)
    expect(page.get_by_role("heading", name="Lists")).to_be_visible()
    expect(page.get_by_role("link", name="Users")).to_have_count(0)

    # And typing the URL in does not get them there either.
    page.goto(f"{base_url}/admin")
    expect(page.get_by_role("heading", name="Lists")).to_be_visible()
    expect(page.get_by_role("heading", name="Users")).to_have_count(0)
