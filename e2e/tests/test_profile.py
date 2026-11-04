"""
A member's own settings (requirements/0004 R33, R34).

Needs E2E_ADMIN_PASSWORD, because every password test here works on an account
the run created rather than on Ana or Ben. Changing a seeded member's password
would break the sign-in every other test in this suite depends on - and in dev
it would stay broken until someone noticed.
"""

from __future__ import annotations

import io
import struct
import zlib

import pytest
from playwright.sync_api import Page, expect

from conftest import DemoMember, TestData, api_csrf_header, profile_link, ui_sign_in


def sign_out(page: Page, base_url: str) -> None:
    """
    Sign out and wait to actually be out.

    Navigating straight after the click cancels the logout POST in flight, the
    session survives, and /login bounces back to the app - so the next
    pick-a-user step finds no roster at all.
    """
    # From /lists, not from wherever we were. ProtectedRoute remembers the
    # screen you were sent away from, so signing in again resumes there - and
    # conftest's ui_sign_in waits for the Lists heading.
    page.goto(f"{base_url}/lists")
    expect(page.get_by_role("heading", name="Lists")).to_be_visible()
    page.get_by_role("button", name="Sign out").click()
    expect(page.get_by_role("heading", name="Who's signing in?")).to_be_visible()


def expect_signed_in(page: Page, member: DemoMember) -> None:
    """
    Signed in, without caring which screen.

    Signing out of /profile and back in resumes there rather than on /lists -
    ProtectedRoute remembers where you were, which is what makes a shared link
    survive a sign-in. The sidebar names the member on every signed-in screen,
    so that is the stable signal.
    """
    expect(profile_link(page, member)).to_be_visible()


@pytest.fixture
def disposable_member(admin_api, cleanup_user, test_data: TestData) -> DemoMember:
    """A passwordless member this test may lock out and throw away."""
    username = test_data.username("profile")
    # The display name has to be unique too, not just the login name: these
    # accounts coexist until session-end cleanup, and two sign-in rows reading
    # the same would make every by-name selector ambiguous.
    response = admin_api.post(
        "/api/admin/users",
        json={
            "username": username,
            "displayName": test_data.name(f"Profile {username.rsplit('-', 1)[-1]}"),
        },
        headers=api_csrf_header(admin_api),
    )
    response.raise_for_status()
    created = response.json()
    cleanup_user(username)
    return DemoMember(
        username=username, display_name=created["displayName"], password=None
    )


@pytest.mark.profile
def test_member_sets_their_own_password_then_clears_it_again(
    page: Page, base_url: str, disposable_member: DemoMember
) -> None:
    ui_sign_in(page, base_url, disposable_member)

    page.goto(f"{base_url}/profile")
    page.get_by_role("textbox", name="New password", exact=True).fill("a-new-secret")
    page.get_by_role("button", name="Save").click()
    expect(page.get_by_text("asked for this next time")).to_be_visible()

    # Signing out and back in is the only honest proof the hash landed.
    sign_out(page, base_url)

    page.get_by_role("button", name=disposable_member.display_name).click()
    expect(page.get_by_text("Signing in as")).to_be_visible()
    page.get_by_role("textbox", name="Password", exact=True).fill("a-new-secret")
    page.get_by_role("button", name="Sign in", exact=True).click()
    expect_signed_in(page, disposable_member)

    # And off again: clearing it is what turns the requirement off entirely,
    # because password_hash IS NULL is the only flag there is (adr/0005).
    page.goto(f"{base_url}/profile")
    page.get_by_role("switch", name="Ask for a password when I sign in").uncheck()
    page.get_by_role("button", name="Save").click()
    expect(page.get_by_text("sign in with one tap")).to_be_visible()

    sign_out(page, base_url)
    page.get_by_role("button", name=disposable_member.display_name).click()
    expect_signed_in(page, disposable_member)


@pytest.mark.profile
def test_admin_clears_a_password_a_member_locked_themselves_out_with(
    page: Page,
    base_url: str,
    admin_member: DemoMember,
    disposable_member: DemoMember,
) -> None:
    """The recovery path that makes R33's accepted lockout survivable."""
    ui_sign_in(page, base_url, disposable_member)
    page.goto(f"{base_url}/profile")
    page.get_by_role("textbox", name="New password", exact=True).fill("forgotten")
    page.get_by_role("button", name="Save").click()
    expect(page.get_by_text("asked for this next time")).to_be_visible()
    sign_out(page, base_url)

    ui_sign_in(page, base_url, admin_member)
    page.goto(f"{base_url}/admin")
    page.get_by_role("button", name=disposable_member.display_name).click()
    page.get_by_role("button", name="Remove password").click()
    expect(page.get_by_text("one tap")).to_be_visible()
    sign_out(page, base_url)

    page.get_by_role("button", name=disposable_member.display_name).click()
    expect_signed_in(page, disposable_member)


@pytest.mark.profile
def test_colour_scheme_is_stored_against_the_account_and_survives_a_reload(
    page: Page, base_url: str, disposable_member: DemoMember
) -> None:
    """
    Uses a throwaway account on purpose. The preference is per-member and
    persists, so doing this to Ana or Ben would leave dev with a colour scheme
    somebody did not choose - and the next run inheriting it.
    """
    ui_sign_in(page, base_url, disposable_member)
    page.goto(f"{base_url}/profile")

    # The radio moves optimistically, so waiting for it proves nothing about
    # the server. Wait for the write itself before reloading, or the reload
    # cancels it in flight.
    with page.expect_response(
        lambda r: r.url.endswith("/api/me/preferences") and r.request.method == "PATCH"
    ):
        page.get_by_role("radio", name="Dark", exact=True).check()

    page.reload()
    expect(page.get_by_role("radio", name="Dark", exact=True)).to_be_checked()
    assert (
        page.evaluate("document.documentElement.getAttribute('data-color-scheme')")
        == "dark"
    )

    with page.expect_response(
        lambda r: r.url.endswith("/api/me/preferences") and r.request.method == "PATCH"
    ):
        page.get_by_role("radio", name="Match my device").check()

    page.reload()
    expect(page.get_by_role("radio", name="Match my device")).to_be_checked()


def tiny_png() -> bytes:
    """
    A real 1x1 PNG, built here rather than committed as a fixture.

    The server decides the type from the bytes, so a file that merely ends in
    .png would be refused - which is the point of that rule.
    """

    def chunk(kind: bytes, payload: bytes) -> bytes:
        return (
            struct.pack(">I", len(payload))
            + kind
            + payload
            + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)
        )

    header = struct.pack(">IIBBBBB", 1, 1, 8, 2, 0, 0, 0)
    pixel = zlib.compress(b"\x00\xff\x00\x00")
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", pixel) + chunk(b"IEND", b"")


@pytest.mark.profile
def test_a_members_picture_follows_them_to_the_sidebar_and_the_sign_in_screen(
    page: Page, base_url: str, disposable_member: DemoMember
) -> None:
    ui_sign_in(page, base_url, disposable_member)
    page.goto(f"{base_url}/profile")

    page.get_by_label("Profile picture").set_input_files(
        files=[{"name": "me.png", "mimeType": "image/png", "buffer": tiny_png()}]
    )
    expect(page.get_by_role("button", name="Change picture")).to_be_visible()

    # The bytes really come back, rather than a broken image element.
    src = page.locator("img").first.get_attribute("src")
    assert src is not None and src.startswith("/api/images/")
    assert page.request.get(f"{base_url}{src}").status == 200

    # It is on the sign-in screen too, which is the whole argument of adr/0019.
    sign_out(page, base_url)
    row = page.get_by_role("button", name=disposable_member.display_name)
    expect(row.locator("img")).to_have_count(1)
    # And the row still says only their name (R17).
    expect(row).to_have_text(disposable_member.display_name)


@pytest.mark.profile
def test_a_picture_can_be_taken_away_again(
    page: Page, base_url: str, disposable_member: DemoMember
) -> None:
    ui_sign_in(page, base_url, disposable_member)
    page.goto(f"{base_url}/profile")
    page.get_by_label("Profile picture").set_input_files(
        files=[{"name": "me.png", "mimeType": "image/png", "buffer": tiny_png()}]
    )
    expect(page.get_by_role("button", name="Change picture")).to_be_visible()
    src = page.locator("img").first.get_attribute("src")

    page.get_by_role("button", name="Remove").click()

    expect(page.get_by_role("button", name="Add a picture")).to_be_visible()
    assert src is not None
    assert page.request.get(f"{base_url}{src}").status == 404


@pytest.mark.profile
def test_something_that_is_not_an_image_is_refused_however_it_is_named(
    page: Page, base_url: str, disposable_member: DemoMember
) -> None:
    ui_sign_in(page, base_url, disposable_member)
    page.goto(f"{base_url}/profile")

    page.get_by_label("Profile picture").set_input_files(
        files=[
            {
                "name": "innocent.png",
                "mimeType": "image/png",
                "buffer": b"<svg xmlns='http://www.w3.org/2000/svg'><script/></svg>",
            }
        ]
    )

    expect(page.get_by_text("must be a PNG, JPEG or WebP")).to_be_visible()
