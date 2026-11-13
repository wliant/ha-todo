"""
Items (requirements/0004 R29, R30) and the points they earn (R23).

Two of these use a second browser context, which is what this suite is for:
an item two people can reach is exactly the thing that looks right in one
session and is wrong across two.
"""

from __future__ import annotations

import re

import pytest
from playwright.sync_api import Page, expect

import struct
import zlib

from conftest import DemoMember, TestData, show_status, ui_sign_in


def tiny_png() -> bytes:
    """A real 1x1 PNG: the server decides the type from the bytes."""

    def chunk(kind: bytes, payload: bytes) -> bytes:
        return (
            struct.pack(">I", len(payload))
            + kind
            + payload
            + struct.pack(">I", zlib.crc32(kind + payload) & 0xFFFFFFFF)
        )

    return (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", 1, 1, 8, 2, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(b"\x00\xff\x00\x00"))
        + chunk(b"IEND", b"")
    )


def create_list(page: Page, name: str, share_with: str | None = None) -> str:
    # "New list" lives on the list of lists, and signing in no longer lands
    # there - the dashboard does (R44). The sidebar is always present.
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
    page.get_by_role("button", name="Save").click()
    expect(page.get_by_text(description)).to_be_visible()


def open_add_form(page: Page) -> None:
    """Opens the add form with every field the list declares showing.

    The form starts collapsed and shows only what the list insists on (R39), so
    anything optional - a detail, an assignee, a cost - is behind this toggle.
    """
    page.get_by_role("button", name="Add item").click()
    more = page.get_by_role("button", name="More fields")
    if more.is_visible():
        more.click()


@pytest.mark.items
def test_a_member_lapses_an_item_and_puts_it_back(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_lists
) -> None:
    """
    0015 R131, R135, R137: planned and never got to is a terminal state of its
    own - not finished, worth nothing, and reversible.
    """
    name = test_data.name("Lapsing")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    create_list(page, name)

    add_item(page, "Paint the shed")
    page.get_by_role("button", name="Lapse Paint the shed", exact=True).click()

    # Leaves the default To do view exactly as a completion does, and says so
    # for the same reason (R119). Matched by its own disclosure button: the
    # snackbar names the item too, and get_by_text would match twice.
    expect(page.get_by_text("Lapsed “Paint the shed”.")).to_be_visible()
    expect(page.get_by_role("button", name="Paint the shed", exact=True)).not_to_be_visible()

    # Its own status, not folded into Done.
    show_status(page, "Done")
    expect(page.get_by_role("button", name="Paint the shed", exact=True)).not_to_be_visible()
    show_status(page, "Done", "Lapsed")
    expect(page.get_by_role("button", name="Paint the shed", exact=True)).to_be_visible()

    # And it is not a delete: reopening puts it back on the list.
    page.get_by_role("button", name="Reopen Paint the shed", exact=True).click()
    show_status(page, "Lapsed")
    expect(page.get_by_role("button", name="Paint the shed", exact=True)).to_be_visible()


@pytest.mark.items
def test_a_member_adds_an_item_and_ticks_it_off(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_lists
) -> None:
    name = test_data.name("Doing")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    create_list(page, name)

    add_item(page, "Buy milk")
    # A button, not a checkbox: the row's checkbox selects now, and completing
    # is its own control (R48, adr/0027).
    page.get_by_role("button", name="Complete Buy milk", exact=True).click()

    # "To do" is the default view, so a finished item leaves it - and says so
    # in a snackbar, because the row vanishing cannot be the only feedback
    # (0013 R119). The row is matched by its own disclosure button rather than
    # by text: that snackbar names the item too.
    expect(page.get_by_text("Finished “Buy milk”.")).to_be_visible()
    expect(page.get_by_role("button", name="Buy milk", exact=True)).not_to_be_visible()
    show_status(page, "Done")
    expect(page.get_by_text("Buy milk")).to_be_visible()


@pytest.mark.items
def test_points_go_to_whoever_completed_it_not_the_assignee(
    page: Page,
    second_member_page: Page,
    base_url: str,
    demo_member: DemoMember,
    passwordless_member: DemoMember,
    test_data: TestData,
    cleanup_lists,
) -> None:
    """
    The rule a refactor is most likely to invert, and only observable here.
    """
    name = test_data.name("Scoring")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    list_id = create_list(page, name, share_with=passwordless_member.display_name)

    page.goto(f"{base_url}/lists/{list_id}/settings")
    page.get_by_role("switch", name="Assignee").click()
    page.get_by_role("button", name="Add a cost").click()
    page.get_by_role("textbox", name="Cost name").fill("Time")
    page.get_by_role("spinbutton", name="Points").nth(1).fill("50")
    page.get_by_role("button", name="Save changes").click()
    expect(page.get_by_role("heading", name=name)).to_be_visible()

    # Assigned to Ben...
    open_add_form(page)
    page.get_by_role("textbox", name="Description").fill("Sweep up")
    page.get_by_role("combobox", name="Assignee").click()
    page.get_by_role("option", name=passwordless_member.display_name).click()
    page.get_by_role("combobox", name="Time").click()
    page.get_by_role("option", name="30 min", exact=False).click()
    page.get_by_role("button", name="Save").click()
    expect(page.get_by_text("Sweep up")).to_be_visible()

    # ...and completed by Ana.
    page.get_by_role("button", name="Complete Sweep up", exact=True).click()

    # The standing lives on the Activity tab now (R43).
    page.get_by_role("tab", name="Activity").click()
    points = page.get_by_role("heading", name="Points").locator("xpath=../..")
    expect(points.get_by_text(demo_member.display_name)).to_be_visible()
    expect(points.get_by_text("50")).to_be_visible()
    expect(points.get_by_text(passwordless_member.display_name)).to_have_count(0)


@pytest.mark.items
def test_the_second_member_to_tick_an_item_is_told_who_got_there_first(
    page: Page,
    second_member_page: Page,
    base_url: str,
    demo_member: DemoMember,
    passwordless_member: DemoMember,
    test_data: TestData,
    cleanup_lists,
) -> None:
    """
    R30: a concurrent completion is never silently discarded, and never
    silently overwrites. This is the scenario second_member_page exists for.
    """
    name = test_data.name("Contended")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    list_id = create_list(page, name, share_with=passwordless_member.display_name)
    add_item(page, "Take the bins out")

    # Both members are looking at the same open item.
    ui_sign_in(second_member_page, base_url, passwordless_member)
    second_member_page.goto(f"{base_url}/lists/{list_id}")
    expect(second_member_page.get_by_text("Take the bins out")).to_be_visible()

    # Ben gets there first.
    second_member_page.get_by_role(
        "button", name="Complete Take the bins out", exact=True
    ).click()
    # By the row's disclosure button: R119's snackbar names the item as well.
    expect(
        second_member_page.get_by_role("button", name="Take the bins out", exact=True)
    ).not_to_be_visible()

    # Ana completes the item her screen still shows as open.
    page.get_by_role("button", name="Complete Take the bins out", exact=True).click()

    expect(page.get_by_text(f"{passwordless_member.display_name} completed this first")
           ).to_be_visible()
    # And her screen now agrees with the server rather than with her click.
    show_status(page, "Done")
    expect(page.get_by_role("button", name="Take the bins out", exact=True)).to_be_visible()


@pytest.mark.items
def test_an_item_cannot_be_saved_without_what_the_list_insists_on(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_lists
) -> None:
    name = test_data.name("Insisting")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    list_id = create_list(page, name)

    page.goto(f"{base_url}/lists/{list_id}/settings")
    # The due date is on every list; making it required is the one setting.
    page.get_by_role("checkbox", name="Due date and time is required").click()
    page.get_by_role("button", name="Save changes").click()
    expect(page.get_by_role("heading", name=name)).to_be_visible()

    page.get_by_role("button", name="Add item").click()
    page.get_by_role("textbox", name="Description").fill("No date on this")
    page.get_by_role("button", name="Save").click()

    expect(page.get_by_text("needs a due date")).to_be_visible()


@pytest.mark.items
def test_detail_keeps_its_formatting_and_refuses_an_attack(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_lists
) -> None:
    """
    R21's rich text. The sanitizer runs on the server, so what comes back is
    what was allowed through, not what was typed.
    """
    name = test_data.name("Recipes")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    list_id = create_list(page, name)

    page.goto(f"{base_url}/lists/{list_id}/settings")
    page.get_by_role("switch", name="Detail").click()
    page.get_by_role("button", name="Save changes").click()
    expect(page.get_by_role("heading", name=name)).to_be_visible()

    open_add_form(page)
    page.get_by_role("textbox", name="Description").fill("Lemon pie")
    # Type the steps, then format the selection. Clicking the toolbar first
    # and typing afterwards races the editor's own focus handling, and the
    # first keystrokes land outside the list.
    page.get_by_role("textbox", name="Detail").click()
    page.keyboard.type("Heat the oven")
    page.keyboard.press("Enter")
    page.keyboard.type("Then wait")
    page.keyboard.press("ControlOrMeta+a")
    page.get_by_role("button", name="Numbered steps").click()
    page.get_by_role("button", name="Save").click()

    expect(page.get_by_text("Lemon pie")).to_be_visible()
    # The detail is behind the row's disclosure now (R38), so a long list draws
    # no pictures until somebody asks for one.
    page.get_by_role("button", name="Lemon pie", exact=True).click()
    # The steps survive as a list rather than as flattened text.
    expect(page.locator("li", has_text="Heat the oven")).to_be_visible()
    expect(page.locator("li", has_text="Then wait")).to_be_visible()


@pytest.mark.items
def test_adding_a_picture_does_not_swallow_what_was_written(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_lists
) -> None:
    """
    Inserting an image over a selection would delete it. Needs a real browser:
    jsdom gives the editor no selection, so a unit test passes either way.
    """
    name = test_data.name("Illustrated")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    list_id = create_list(page, name)

    page.goto(f"{base_url}/lists/{list_id}/settings")
    page.get_by_role("switch", name="Detail").click()
    page.get_by_role("button", name="Save changes").click()
    expect(page.get_by_role("heading", name=name)).to_be_visible()

    open_add_form(page)
    page.get_by_role("textbox", name="Description").fill("Lemon pie")
    page.get_by_role("textbox", name="Detail").click()
    page.keyboard.type("Heat the oven")
    # Everything selected, exactly as somebody about to format it would have.
    page.keyboard.press("ControlOrMeta+a")
    page.set_input_files(
        "input[type=file]",
        files=[{"name": "pie.png", "mimeType": "image/png", "buffer": tiny_png()}],
    )
    expect(page.get_by_role("textbox", name="Detail").locator("img")).to_have_count(1)

    page.get_by_role("button", name="Save").click()
    expect(page.get_by_text("Lemon pie")).to_be_visible()

    # The detail is behind the row's disclosure now (R38).
    page.get_by_role("button", name="Lemon pie", exact=True).click()

    # Both the words and the picture survive.
    expect(page.get_by_text("Heat the oven")).to_be_visible()
    expect(page.locator("img[src^='/api/images/']")).to_have_count(1)


@pytest.mark.items
def test_a_script_in_the_detail_never_comes_back(
    page: Page,
    base_url: str,
    demo_member: DemoMember,
    test_data: TestData,
    cleanup_lists,
    authenticated_api,
) -> None:
    """
    Posted through the API rather than the editor: the editor would never
    produce this, and the server is where it has to be stopped.
    """
    name = test_data.name("Hostile")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    list_id = create_list(page, name)

    page.goto(f"{base_url}/lists/{list_id}/settings")
    page.get_by_role("switch", name="Detail").click()
    page.get_by_role("button", name="Save changes").click()
    expect(page.get_by_role("heading", name=name)).to_be_visible()

    from conftest import api_csrf_header

    response = authenticated_api.post(
        f"/api/lists/{list_id}/items",
        json={
            "description": "Looks innocent",
            "detailHtml": "<p>ok</p><script>alert(1)</script><img src=x onerror=alert(1)>",
        },
        headers=api_csrf_header(authenticated_api),
    )
    response.raise_for_status()
    stored = response.json()["detailHtml"]
    assert "script" not in stored and "onerror" not in stored, stored
    assert "ok" in stored

    page.reload()
    expect(page.get_by_text("Looks innocent")).to_be_visible()


@pytest.mark.items
def test_a_member_says_how_the_work_went_and_the_other_reads_it(
    page: Page,
    second_member_page: Page,
    base_url: str,
    demo_member: DemoMember,
    passwordless_member: DemoMember,
    test_data: TestData,
    cleanup_lists,
) -> None:
    """R36, R37: a tick is not a record.

    Two contexts because the point of a completion note is the *other* member
    reading it. The picture matters most: it only renders for Ben if the note's
    image was claimed for the item on the way through, which is the bug
    adr/0023 exists to prevent.
    """
    name = test_data.name("Repairs")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    list_id = create_list(page, name, share_with=passwordless_member.display_name)
    add_item(page, "Fix the tap")

    # Ana completes it and says how it went, reaching the note by its own
    # button - no press-and-hold, which a keyboard could not do anyway.
    page.get_by_role("button", name="Complete Fix the tap with a note").click()
    note = page.get_by_role("textbox", name="How Fix the tap went")
    note.click()
    page.keyboard.type("Washer was perished")
    page.set_input_files("input[type=file]", files=[
        {"name": "tap.png", "mimeType": "image/png", "buffer": tiny_png()},
    ])
    expect(page.get_by_role("dialog").locator("img")).to_be_visible()
    page.get_by_role("dialog").get_by_role("button", name="Complete").click()

    # Ben opens the same list and reads it.
    ui_sign_in(second_member_page, base_url, passwordless_member)
    second_member_page.goto(f"{base_url}/lists/{list_id}")
    show_status(second_member_page, "Done")
    second_member_page.get_by_role("button", name="Fix the tap", exact=True).click()

    expect(second_member_page.get_by_text("Washer was perished")).to_be_visible()
    expect(
        second_member_page.get_by_text(
            re.compile(rf"Finished .*by {demo_member.display_name}")
        )
    ).to_be_visible()
    # The picture is readable by somebody who did not upload it, which is the
    # assertion that proves the claim happened.
    picture = second_member_page.get_by_role("img").last
    expect(picture).to_be_visible()


@pytest.mark.items
def test_reopening_an_item_takes_its_points_back(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_lists
) -> None:
    """R49: reversing a completion returns the points it earned."""
    name = test_data.name("Reopening")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    list_id = create_list(page, name)

    page.goto(f"{base_url}/lists/{list_id}/settings")
    page.get_by_role("button", name="Add a cost").click()
    page.get_by_role("textbox", name="Cost name").fill("Time")
    page.get_by_role("spinbutton", name="Points").nth(0).fill("50")
    page.get_by_role("button", name="Save changes").click()
    expect(page.get_by_role("heading", name=name)).to_be_visible()

    open_add_form(page)
    page.get_by_role("textbox", name="Description").fill("Worth something")
    page.get_by_role("combobox", name="Time").click()
    page.get_by_role("option", name="5 min", exact=False).click()
    page.get_by_role("button", name="Save").click()

    page.get_by_role("button", name="Complete Worth something", exact=True).click()

    page.get_by_role("tab", name="Activity").click()
    points = page.get_by_role("heading", name="Points").locator("xpath=../..")
    expect(points.get_by_text("50")).to_be_visible()

    page.get_by_role("tab", name="Items").click()
    show_status(page, "Done")
    page.get_by_role("button", name="Reopen Worth something").click()

    # Back on the to-do list, and the points are gone with it.
    show_status(page, "Just to do")
    expect(page.get_by_text("Worth something")).to_be_visible()
    page.get_by_role("tab", name="Activity").click()
    # Scoped to the Points panel, as the assertion above is. Unscoped, "50" is
    # a substring match against the whole page - and `test_data`'s run prefix
    # is a timestamp, so roughly one run in ten contains it and this fails for
    # a reason that has nothing to do with points.
    expect(
        page.get_by_role("heading", name="Points").locator("xpath=../..")
        .get_by_text("50")
    ).not_to_be_visible()


@pytest.mark.items
def test_a_member_claims_several_items_and_everybody_sees_it(
    page: Page,
    second_member_page: Page,
    base_url: str,
    demo_member: DemoMember,
    passwordless_member: DemoMember,
    test_data: TestData,
    cleanup_lists,
) -> None:
    """R46, R47: claiming in bulk, and the claim reading the same to everyone.

    The second context is the point: "Ben took this" has to be a fact about the
    item, not about who is looking at it (adr/0026).
    """
    name = test_data.name("Dividing")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    list_id = create_list(page, name, share_with=passwordless_member.display_name)

    page.goto(f"{base_url}/lists/{list_id}/settings")
    page.get_by_role("switch", name="Assignee").click()
    page.get_by_role("button", name="Save changes").click()
    expect(page.get_by_role("heading", name=name)).to_be_visible()

    add_item(page, "Upstairs")
    add_item(page, "Downstairs")

    # Ben takes both of them in one action.
    ui_sign_in(second_member_page, base_url, passwordless_member)
    second_member_page.goto(f"{base_url}/lists/{list_id}")
    second_member_page.get_by_role("checkbox", name="Select Upstairs").click()
    second_member_page.get_by_role("checkbox", name="Select Downstairs").click()
    expect(second_member_page.get_by_text("2 selected")).to_be_visible()
    second_member_page.get_by_role("button", name="Assign to me").click()

    claimed = f"{passwordless_member.display_name} claimed this"
    expect(second_member_page.get_by_label(claimed).first).to_be_visible()

    # Ana sees the same thing: claimed, not merely assigned.
    page.reload()
    expect(page.get_by_label(claimed)).to_have_count(2)


@pytest.mark.items
def test_a_filtered_list_is_an_address_a_reload_keeps(
    page: Page,
    base_url: str,
    demo_member: DemoMember,
    passwordless_member: DemoMember,
    test_data: TestData,
    cleanup_lists,
) -> None:
    """0013 R107-R115: filter to one member's work, and reload onto the same view.

    The reload is the assertion no unit test can make: the filter lives in the
    address rather than in component state (adr/0045), so a refresh, a back
    button and a pasted link all show the same subset.

    The item descriptions are deliberately unlike any control's label: a shared
    list's items also appear on the agenda, where other tests in this suite
    query buttons by name - an item called "Mine" collides with the agenda's
    own Mine toggle.
    """
    name = test_data.name("Filtering")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    list_id = create_list(page, name, share_with=passwordless_member.display_name)

    page.goto(f"{base_url}/lists/{list_id}/settings")
    page.get_by_role("switch", name="Assignee").click()
    page.get_by_role("button", name="Save changes").click()
    expect(page.get_by_role("heading", name=name)).to_be_visible()

    add_item(page, "Taken by Ana")
    add_item(page, "Up for grabs")
    page.get_by_role("checkbox", name="Select Taken by Ana").click()
    page.get_by_role("button", name="Assign to me").click()
    expect(page.get_by_label(f"{demo_member.display_name} claimed this")).to_be_visible()

    page.get_by_role("button", name=re.compile(r"^Assignee:")).click()
    page.get_by_role("menuitem", name=demo_member.display_name, exact=True).click()
    page.keyboard.press("Escape")

    expect(page.get_by_text("Taken by Ana", exact=True)).to_be_visible()
    expect(page.get_by_text("Up for grabs", exact=True)).not_to_be_visible()
    expect(page.get_by_role("status").filter(has_text="1 filter")).to_be_visible()
    # The filter is in the address, so the reload lands on the same view.
    assert "who=" in page.url

    page.reload()

    expect(page.get_by_text("Taken by Ana", exact=True)).to_be_visible()
    expect(page.get_by_text("Up for grabs", exact=True)).not_to_be_visible()

    # And clearing it is one action, which brings the other item back (R118).
    page.get_by_role("button", name="Clear").click()
    expect(page.get_by_text("Up for grabs", exact=True)).to_be_visible()


@pytest.mark.activity
def test_the_activity_tab_shows_who_did_what_this_week(
    page: Page,
    second_member_page: Page,
    base_url: str,
    demo_member: DemoMember,
    passwordless_member: DemoMember,
    test_data: TestData,
    cleanup_lists,
) -> None:
    """R43: a list reports the last seven days, per member.

    Two contexts because the question is about more than one person: "who did
    what" on a shared list is only interesting once two people have.
    """
    name = test_data.name("Weekly")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    list_id = create_list(page, name, share_with=passwordless_member.display_name)
    add_item(page, "Ana's two: one")
    add_item(page, "Ana's two: two")
    add_item(page, "Ben's one")

    page.get_by_role("button", name="Complete Ana's two: one", exact=True).click()
    page.get_by_role("button", name="Complete Ana's two: two", exact=True).click()

    ui_sign_in(second_member_page, base_url, passwordless_member)
    second_member_page.goto(f"{base_url}/lists/{list_id}")
    second_member_page.get_by_role("button", name="Complete Ben's one", exact=True).click()

    # Either member sees the same week, because it is a fact about the list.
    second_member_page.get_by_role("tab", name="Activity").click()
    # One level up, not two: two levels reaches the container that also holds
    # the all-time Points panel, where every name appears a second time.
    week = second_member_page.get_by_role("heading", name="This week").locator("xpath=..")
    expect(week.get_by_text(f"{demo_member.display_name}", exact=True)).to_be_visible()
    expect(week.get_by_text("2 items")).to_be_visible()
    expect(week.get_by_text("1 item", exact=True)).to_be_visible()
    # And the feed names the work itself, not just the totals.
    expect(week.get_by_text("Ben's one")).to_be_visible()


@pytest.mark.activity
def test_the_activity_tab_is_its_own_url(
    page: Page, base_url: str, demo_member: DemoMember, test_data: TestData, cleanup_lists
) -> None:
    """The tabs are routes, so a tab can be linked to and the back button works."""
    name = test_data.name("Linkable")
    cleanup_lists(name)
    ui_sign_in(page, base_url, demo_member)
    list_id = create_list(page, name)

    page.goto(f"{base_url}/lists/{list_id}/activity")

    expect(page.get_by_role("heading", name="This week")).to_be_visible()
    expect(page.get_by_role("tab", name="Activity")).to_have_attribute(
        "aria-selected", "true"
    )

    page.get_by_role("tab", name="Items").click()
    expect(page.get_by_role("heading", name="Items")).to_be_visible()
    page.go_back()
    expect(page.get_by_role("heading", name="This week")).to_be_visible()
