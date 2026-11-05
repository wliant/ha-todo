"""
The signed-in navigation shell (requirements/0004 R25).

The phone-width drawer is the one place the design admits a piece of layout
state (adr/0015), so it is the one piece of layout worth asserting end to end.
"""

from __future__ import annotations

import pytest
from playwright.sync_api import Page, expect

from conftest import DemoMember, profile_link, ui_sign_in

PHONE = {"width": 390, "height": 844}
DESKTOP = {"width": 1280, "height": 900}


@pytest.mark.shell
def test_sidebar_is_always_on_screen_on_a_wide_window(
    page: Page, base_url: str, passwordless_member: DemoMember
) -> None:
    page.set_viewport_size(DESKTOP)
    ui_sign_in(page, base_url, passwordless_member)

    expect(page.get_by_role("link", name="Lists")).to_be_visible()
    expect(page.get_by_role("button", name="Open navigation")).to_have_count(0)


@pytest.mark.shell
def test_on_a_phone_the_sidebar_opens_from_the_app_bar_and_closes_on_choosing(
    page: Page, base_url: str, passwordless_member: DemoMember
) -> None:
    page.set_viewport_size(PHONE)
    ui_sign_in(page, base_url, passwordless_member)

    expect(page.get_by_role("link", name="Lists")).not_to_be_visible()

    page.get_by_role("button", name="Open navigation").click()
    expect(page.get_by_role("link", name="Lists")).to_be_visible()

    # It must never be left covering the screen it navigated to.
    profile_link(page, passwordless_member).click()
    expect(page.get_by_role("heading", name="Your profile")).to_be_visible()
    expect(page.get_by_role("link", name="Lists")).not_to_be_visible()


@pytest.mark.shell
def test_the_app_bar_does_not_cover_the_page_it_sits_above(
    page: Page, base_url: str, passwordless_member: DemoMember
) -> None:
    """
    An element underneath a fixed bar is still "visible" to a locator, so this
    asserts geometry rather than visibility - which is the only way to catch it.
    """
    page.set_viewport_size(PHONE)
    ui_sign_in(page, base_url, passwordless_member)

    heading = page.get_by_role("heading", name="My day").bounding_box()
    app_bar = page.locator("header").bounding_box()
    assert heading is not None and app_bar is not None
    assert heading["y"] >= app_bar["y"] + app_bar["height"], (
        f"the page heading starts at {heading['y']}px, under an app bar that "
        f"ends at {app_bar['y'] + app_bar['height']}px"
    )


@pytest.mark.shell
def test_nothing_requires_sideways_scrolling_on_a_phone(
    page: Page, base_url: str, passwordless_member: DemoMember
) -> None:
    """R15: a phone-width screen is a first-class layout, not a squeezed one."""
    page.set_viewport_size(PHONE)
    ui_sign_in(page, base_url, passwordless_member)

    overflow = page.evaluate(
        "document.documentElement.scrollWidth - document.documentElement.clientWidth"
    )
    assert overflow <= 0, f"the page scrolls sideways by {overflow}px"
