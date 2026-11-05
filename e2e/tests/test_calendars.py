"""
Subscribed calendars (requirements/0007 R62-R66).

**What this file deliberately does not cover: events rendering on the agenda.** The
only way an event reaches the database is the poller fetching a feed, and a
deployment's URL guard refuses every address on the machine running these tests
(docs/adr/0035) - so there is no way to publish a fixture calendar that dev will
read. Pointing a test at a real public calendar would buy one assertion and an
external dependency that breaks this suite on somebody else's schedule.

That path is covered where a fixture feed can actually be served:
``CalendarFeedApiIntegrationTest`` subscribes a real ``.ics`` over HTTP and
asserts the events land, the grants hold and a revocation takes them away; the
rendering is covered by ``DashboardPage.test.tsx`` and
``CalendarFeeds.test.tsx``. What is left here is the screen a member actually
uses, and the refusals they will actually hit.
"""

from __future__ import annotations

import re

import pytest
from playwright.sync_api import Page, expect

from conftest import DemoMember, ui_sign_in


def open_calendars(page: Page, base_url: str) -> None:
    page.goto(f"{base_url}/profile")
    expect(page.get_by_role("heading", name="Your calendars")).to_be_visible()


def open_add_dialog(page: Page) -> None:
    page.get_by_role("button", name="Add a calendar").click()
    expect(page.get_by_role("heading", name="Add a calendar")).to_be_visible()


def subscribed_count(page: Page) -> int:
    """
    How many calendars this member has, by their remove buttons.

    Counted rather than compared against an empty state: dev is a real
    deployment, and a member who has legitimately subscribed a calendar must
    not make this suite fail.
    """
    return page.get_by_role("button", name=re.compile(r"^Remove ")).count()


@pytest.fixture(autouse=True)
def signed_in(page: Page, base_url: str, demo_member: DemoMember) -> None:
    ui_sign_in(page, base_url, demo_member)


def test_the_profile_explains_what_a_subscribed_calendar_is(
    page: Page, base_url: str
) -> None:
    open_calendars(page, base_url)

    # The two things a member has to believe before pasting a secret address:
    # it is read-only, and nobody sees it until they say so.
    expect(page.get_by_text("Nothing here is ever written back", exact=False)).to_be_visible()


def test_adding_a_calendar_explains_where_the_address_lives(
    page: Page, base_url: str
) -> None:
    open_calendars(page, base_url)
    open_add_dialog(page)

    # Onboarding is a trip through somebody else's settings screen, so the
    # instructions are the feature (requirements/0007 consequence 2).
    expect(page.get_by_text("Secret address in iCal format", exact=False)).to_be_visible()
    expect(page.get_by_text("Publish a calendar", exact=False)).to_be_visible()
    expect(page.get_by_label("Calendar address")).to_be_visible()


def test_nothing_is_subscribed_until_the_member_has_seen_what_came_back(
    page: Page, base_url: str
) -> None:
    """R65: the preview is a step, not a nicety."""
    open_calendars(page, base_url)
    open_add_dialog(page)

    # No save button exists yet - the only way forward is to look at it first.
    expect(page.get_by_role("button", name="Add calendar", exact=True)).to_have_count(0)
    expect(page.get_by_role("button", name="Look at it")).to_be_disabled()


def test_an_address_that_cannot_be_read_is_refused_and_nothing_is_saved(
    page: Page, base_url: str
) -> None:
    open_calendars(page, base_url)
    before = subscribed_count(page)
    open_add_dialog(page)

    # .invalid is reserved by RFC 2606 and resolves nowhere, in dev and on a
    # laptop alike - so this is the "wrong address" path without depending on
    # anybody else's uptime.
    page.get_by_label("Calendar address").fill("https://nothing.invalid/basic.ics")
    page.get_by_role("button", name="Look at it").click()

    expect(page.get_by_role("alert")).to_be_visible()
    # Still on step one: there is nothing to confirm.
    expect(page.get_by_role("heading", name="Who can see this?")).to_have_count(0)

    page.get_by_role("button", name="Cancel").click()
    expect(page.get_by_role("heading", name="Your calendars")).to_be_visible()
    assert subscribed_count(page) == before, "a refused address must subscribe nothing"


def test_an_address_that_is_not_a_web_address_is_refused_outright(
    page: Page, base_url: str
) -> None:
    """
    The guard, through the UI (docs/adr/0035).

    A non-web scheme is refused in every configuration - unlike a private host,
    which the local stack deliberately allows so the integration suite can
    serve itself a fixture feed.
    """
    open_calendars(page, base_url)
    open_add_dialog(page)

    page.get_by_label("Calendar address").fill("ftp://192.168.1.248:5000/v2/_catalog")
    page.get_by_role("button", name="Look at it").click()

    expect(page.get_by_role("alert")).to_be_visible()
    expect(page.get_by_role("heading", name="Who can see this?")).to_have_count(0)


def test_the_agenda_still_reads_as_a_day_with_no_calendars_subscribed(
    page: Page, base_url: str
) -> None:
    """
    The empty state has to say both halves now.

    Before calendars, an empty day meant "nothing is due". It now also means
    "and nothing is happening", which is the sentence that keeps a member from
    reading silence as a promise (R71's quieter cousin).
    """
    page.goto(f"{base_url}/dashboard/day")
    expect(page.get_by_role("heading", name="Agenda")).to_be_visible()
    expect(page.get_by_role("heading", name="Today")).to_be_visible()
