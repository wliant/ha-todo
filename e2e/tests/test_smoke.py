"""Smoke checks: the environment is alive and a member can get in."""

from __future__ import annotations

import httpx
import pytest
from playwright.sync_api import Page, expect


@pytest.mark.smoke
def test_health_endpoint_reports_up(api: httpx.Client) -> None:
    response = api.get("/actuator/health")

    assert response.status_code == 200
    assert response.json()["status"] == "UP"


@pytest.mark.smoke
def test_login_page_renders(page: Page, base_url: str) -> None:
    page.goto(f"{base_url}/login")

    expect(page.get_by_label("Email")).to_be_visible()
    expect(page.get_by_label("Password")).to_be_visible()
    expect(page.get_by_role("button", name="Sign in")).to_be_enabled()


@pytest.mark.smoke
def test_demo_member_can_sign_in(
    page: Page, base_url: str, demo_credentials: tuple[str, str]
) -> None:
    email, password = demo_credentials

    page.goto(f"{base_url}/login")
    page.get_by_label("Email").fill(email)
    page.get_by_label("Password").fill(password)
    page.get_by_role("button", name="Sign in").click()

    expect(page.get_by_role("heading", name="Lists")).to_be_visible()
    expect(page.get_by_text("Signed in as")).to_be_visible()


def test_unauthenticated_member_is_sent_to_login(page: Page, base_url: str) -> None:
    page.goto(f"{base_url}/lists")

    expect(page.get_by_role("button", name="Sign in")).to_be_visible()


def test_sessions_are_isolated_between_members(
    page: Page,
    second_member_page: Page,
    base_url: str,
    demo_credentials: tuple[str, str],
) -> None:
    """One member signing in must not sign the other in."""
    email, password = demo_credentials

    page.goto(f"{base_url}/login")
    page.get_by_label("Email").fill(email)
    page.get_by_label("Password").fill(password)
    page.get_by_role("button", name="Sign in").click()
    expect(page.get_by_role("heading", name="Lists")).to_be_visible()

    second_member_page.goto(f"{base_url}/lists")
    expect(second_member_page.get_by_role("button", name="Sign in")).to_be_visible()


@pytest.mark.smoke
def test_sign_out_returns_to_login(
    page: Page, base_url: str, demo_credentials: tuple[str, str]
) -> None:
    email, password = demo_credentials

    page.goto(f"{base_url}/login")
    page.get_by_label("Email").fill(email)
    page.get_by_label("Password").fill(password)
    page.get_by_role("button", name="Sign in").click()
    expect(page.get_by_role("heading", name="Lists")).to_be_visible()

    page.get_by_role("button", name="Sign out").click()

    expect(page.get_by_role("button", name="Sign in")).to_be_visible()
