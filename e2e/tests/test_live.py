"""
An agenda that keeps itself current (requirements/0021).

This is the suite's reason to exist, in its purest form: one member adds a
chore and *another member's already-open screen* changes. There is no version
of this that can be observed in one browser context.

It is also the only test in the product that exercises the proxy chain as a
behaviour rather than as plumbing. The HTTPS front door in front of both
environments is configured outside this repository; if it buffers the stream,
everything here fails while every unit and integration test stays green. That
is deliberate - see the note on PATIENCE below.
"""

from __future__ import annotations

import pytest
from playwright.sync_api import Page, expect

from conftest import DemoMember, TestData, ui_sign_in

# Deliberately well under the 60-second fallback poll in useLiveUpdates.
#
# A timeout comfortably longer than that would be satisfied by degraded mode,
# which cannot push and cannot name what arrived - so the test would pass
# against a build where the stream never works at all, which is the one
# failure this file exists to catch (adr/0056). If this fails against a
# deployment, the finding is about the front door, not about this number.
PATIENCE = 15_000


def create_list(page: Page, name: str, share_with: str | None = None) -> str:
    page.get_by_role("link", name="Lists").first.click()
    page.get_by_role("link", name="New list").click()
    page.get_by_role("textbox", name="Name", exact=True).fill(name)
    if share_with:
        page.get_by_role("checkbox", name=share_with).click()
    page.get_by_role("button", name="Create list").click()
    expect(page.get_by_role("heading", name=name)).to_be_visible()
    return page.url.rsplit("/", 1)[-1]


def add_item(page: Page, description: str) -> None:
    page.get_by_role("button", name="Add item").click()
    page.get_by_role("textbox", name="Description").fill(description)
    # Today, so it lands on the day view the other member is looking at.
    page.get_by_role("button", name="Today", exact=True).click()
    page.get_by_role("button", name="Save").click()
    expect(page.get_by_text(description)).to_be_visible()


@pytest.mark.live
def test_a_chore_somebody_else_adds_appears_on_an_open_agenda(
    page: Page,
    second_member_page: Page,
    base_url: str,
    demo_member: DemoMember,
    passwordless_member: DemoMember,
    test_data: TestData,
    cleanup_lists,
) -> None:
    """
    R179 and R180: it appears, and the agenda says so by name.

    Ben's agenda is opened *first* and then never touched again - no reload, no
    navigation, no click anywhere on it. Everything that happens to it is the
    consequence of Ana's typing in another browser.
    """
    name = test_data.name("Live")
    cleanup_lists(name)
    chore = f"{name} collect the parcel"

    # Ben parks on the agenda and stops interacting with the browser.
    ui_sign_in(second_member_page, base_url, passwordless_member)
    second_member_page.goto(f"{base_url}/dashboard/day")
    expect(second_member_page.get_by_role("heading", name="Agenda")).to_be_visible()

    # Ana, elsewhere, shares a list and puts something on it.
    ui_sign_in(page, base_url, demo_member)
    create_list(page, name, share_with=passwordless_member.display_name)
    add_item(page, chore)

    # Ben's screen changes on its own. The row is the requirement (R179)...
    expect(second_member_page.get_by_text(chore).first).to_be_visible(timeout=PATIENCE)
    # ...and being told is the other half of it (R180).
    expect(
        second_member_page.get_by_text(
            f"{demo_member.display_name} added {chore} to {name}."
        )
    ).to_be_visible(timeout=PATIENCE)


@pytest.mark.live
def test_a_member_is_not_told_about_their_own_addition(
    page: Page,
    second_member_page: Page,
    base_url: str,
    demo_member: DemoMember,
    test_data: TestData,
    cleanup_lists,
) -> None:
    """
    R181, both halves of it.

    Two sessions belonging to the *same member* - two devices, which is the
    case the rule is actually about. The device that did not do the adding
    still has to refresh, because the chore is genuinely new to it; what it
    must not do is announce the member to themselves.

    Written this way rather than as one tab navigating, because a test that
    navigates passes just as well against a build with no stream at all.
    """
    name = test_data.name("LiveOwn")
    cleanup_lists(name)
    chore = f"{name} water the plants"

    ui_sign_in(page, base_url, demo_member)
    create_list(page, name, share_with=None)

    # Ana's other device, parked on the agenda and never touched again.
    ui_sign_in(second_member_page, base_url, demo_member)
    second_member_page.goto(f"{base_url}/dashboard/day")
    expect(second_member_page.get_by_role("heading", name="Agenda")).to_be_visible()

    add_item(page, chore)

    # It arrives there by itself - the refresh is not suppressed...
    expect(second_member_page.get_by_text(chore).first).to_be_visible(timeout=PATIENCE)
    # ...but she is never told she did something she just did.
    expect(
        second_member_page.get_by_text(f"{demo_member.display_name} added {chore}")
    ).to_have_count(0)
