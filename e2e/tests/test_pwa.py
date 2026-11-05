"""
The installable app and the share target (requirements/0003 R13, R14).

Installation itself cannot be driven from Playwright - no browser exposes it -
so what is asserted here is everything installation depends on: the manifest
and its icons are served, the service worker registers, and the route the
share sheet posts a link to does the right thing with it.
"""

from __future__ import annotations

import re

import httpx
import pytest
from playwright.sync_api import Page, expect

from conftest import DemoMember, TestData, ui_sign_in


@pytest.mark.smoke
def test_manifest_declares_an_installable_app_called_list(api: httpx.Client) -> None:
    response = api.get("/manifest.webmanifest")

    assert response.status_code == 200
    manifest = response.json()
    assert manifest["name"] == "List"
    assert manifest["display"] == "standalone"
    assert {icon["purpose"] for icon in manifest["icons"]} >= {"any", "maskable"}
    assert manifest["share_target"]["action"] == "/share"


@pytest.mark.smoke
def test_every_icon_the_manifest_promises_is_served(api: httpx.Client) -> None:
    manifest = api.get("/manifest.webmanifest").json()
    sources = [icon["src"] for icon in manifest["icons"]] + [
        "/icons/apple-touch-icon-180.png"
    ]

    for src in sources:
        response = api.get(src)
        assert response.status_code == 200, src
        assert response.headers["content-type"] == "image/png", src


def test_service_worker_is_served_and_registers(page: Page, base_url: str) -> None:
    worker = httpx.get(f"{base_url}/sw.js", timeout=20.0, verify=False)
    assert worker.status_code == 200
    # Caching here would serve a stale bundle behind nginx's back (adr/0007),
    # so the worker must not open a cache or answer a request from one. Its own
    # comments say as much, so they are stripped before looking.
    code = re.sub(r"/\*.*?\*/|//[^\n]*", "", worker.text, flags=re.S)
    assert "caches" not in code
    assert "respondWith" not in code

    page.goto(f"{base_url}/login")
    registered = page.evaluate(
        "() => navigator.serviceWorker.ready.then(r => !!r.active).catch(() => false)"
    )
    assert registered is True


def test_a_shared_link_becomes_an_item_on_a_chosen_list(
    page: Page,
    base_url: str,
    demo_member: DemoMember,
    test_data: TestData,
    cleanup_lists,
) -> None:
    """
    What Android's share sheet does: open /share with the link on the URL.

    It becomes a real item now rather than being parked on the device - the
    step adr/0008 left for when items existed (adr/0020).
    """
    name = test_data.name("Shared into")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)

    # Signing in lands on the dashboard (R44); "New list" is on the lists page.
    page.get_by_role("link", name="Lists").first.click()
    page.get_by_role("link", name="New list").click()
    page.get_by_role("textbox", name="Name", exact=True).fill(name)
    page.get_by_role("button", name="Create list").click()
    expect(page.get_by_role("heading", name=name)).to_be_visible()

    page.goto(f"{base_url}/share?title=Lemon+pie&url=https%3A%2F%2Fexample.com%2Fpie")

    expect(page.get_by_role("heading", name="Shared to List")).to_be_visible()
    expect(page.get_by_role("link", name="https://example.com/pie")).to_be_visible()
    # The list they were last on is already chosen, so this is one tap.
    page.get_by_role("button", name="Add it").click()

    expect(page.get_by_role("heading", name=name)).to_be_visible()
    expect(page.get_by_text("Lemon pie - https://example.com/pie")).to_be_visible()


def test_share_arriving_while_signed_out_resumes_after_signing_in(
    second_member_page: Page, base_url: str, passwordless_member: DemoMember
) -> None:
    """A link shared from another app must not be lost to the sign-in screen."""
    page = second_member_page
    page.goto(f"{base_url}/share?url=https%3A%2F%2Fexample.com%2Fresumed")

    expect(page.get_by_role("heading", name="Who's signing in?")).to_be_visible()
    page.get_by_role("button", name=passwordless_member.display_name).click()

    expect(page.get_by_role("heading", name="Shared to List")).to_be_visible()
    expect(page.get_by_role("link", name="https://example.com/resumed")).to_be_visible()
