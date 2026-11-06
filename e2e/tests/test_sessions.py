"""
Staying signed in (requirements/0009 R79-R82).

The assertion that matters here cannot be made by a unit test or by curl: that
**closing the app and coming back leaves a member signed in**. That is R81 as a
member experiences it, and before this it was false - the session cookie had no
lifetime, so the browser discarded it whenever it decided its browsing session
had ended.

Reopening has to be modelled carefully, and the obvious way is wrong.
Playwright's ``storage_state()`` serialises **session cookies too**, so saving
it and restoring it into a fresh context puts them straight back - and a test
built that way passes against a build with no cookie lifetime at all. It cannot
fail, which makes it worse than no test.

``reopened_cold`` therefore drops the cookies a browser drops when its browsing
session ends: the ones with no expiry. What survives is exactly what would
survive quitting the app. Verified by running this file against a deployment of
the previous build, where it fails.
"""

from __future__ import annotations

import pytest
from playwright.sync_api import Browser, expect

from conftest import DemoMember, profile_link, ui_sign_in


def reopened_cold(browser: Browser, base_url: str, state: dict):
    """
    A new context holding only what outlives the browsing session.

    A cookie with no expiry (Playwright reports ``expires`` as -1) is a session
    cookie, and the browser discards it when the app is quit. Restoring it would
    make every assertion in this file vacuous.
    """
    persistent = [cookie for cookie in state.get("cookies", []) if cookie.get("expires", -1) > 0]
    return browser.new_context(
        base_url=base_url, storage_state={**state, "cookies": persistent}
    )


@pytest.mark.smoke
def test_a_member_is_still_signed_in_after_closing_the_app(
    browser: Browser, base_url: str, demo_member: DemoMember
) -> None:
    first = browser.new_context(base_url=base_url)
    page = first.new_page()
    try:
        ui_sign_in(page, base_url, demo_member)
        expect(profile_link(page, demo_member)).to_be_visible()
        saved = first.storage_state()
    finally:
        first.close()

    # What the phone has after the app is quit and opened again.
    reopened = reopened_cold(browser, base_url, saved)
    try:
        resumed = reopened.new_page()
        resumed.goto(f"{base_url}/lists")

        expect(resumed.get_by_role("heading", name="Who's signing in?")).to_have_count(0)
        expect(profile_link(resumed, demo_member)).to_be_visible()
    finally:
        reopened.close()


def test_the_session_cookie_outlives_the_browsing_session(
    browser: Browser, base_url: str, demo_member: DemoMember
) -> None:
    """
    The mechanism behind the test above, asserted directly.

    Without an expiry this is a session cookie and R81 is unreachable whatever
    the server's timeout says - so the attribute is worth pinning on its own,
    where a failure names the cause rather than the symptom.
    """
    context = browser.new_context(base_url=base_url)
    page = context.new_page()
    try:
        ui_sign_in(page, base_url, demo_member)

        session_cookie = next(
            cookie for cookie in context.cookies() if cookie["name"] == "JSESSIONID"
        )

        assert session_cookie["expires"] > 0, "JSESSIONID must not be a session cookie"
        assert session_cookie["httpOnly"] is True
    finally:
        context.close()


def test_signing_out_ends_it_at_once_even_though_it_was_long_lived(
    browser: Browser, base_url: str, demo_member: DemoMember
) -> None:
    """
    R82. The one control a member has over a long session has to work
    completely: a session that survives a deliberate sign-out is what would make
    the whole trade indefensible.
    """
    context = browser.new_context(base_url=base_url)
    page = context.new_page()
    try:
        ui_sign_in(page, base_url, demo_member)
        page.goto(f"{base_url}/lists")
        expect(page.get_by_role("heading", name="Lists")).to_be_visible()

        page.get_by_role("button", name="Sign out").click()
        expect(page.get_by_role("heading", name="Who's signing in?")).to_be_visible()

        saved = context.storage_state()
    finally:
        context.close()

    reopened = reopened_cold(browser, base_url, saved)
    try:
        resumed = reopened.new_page()
        resumed.goto(f"{base_url}/lists")

        # Reopening must not resurrect it.
        expect(resumed.get_by_role("heading", name="Who's signing in?")).to_be_visible()
    finally:
        reopened.close()
