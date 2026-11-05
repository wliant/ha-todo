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

from conftest import DemoMember, TestData, ui_sign_in


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

    # "To do" is the default view, so a finished item leaves it.
    expect(page.get_by_text("Buy milk")).not_to_be_visible()
    page.get_by_role("button", name="Done").click()
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
    expect(second_member_page.get_by_text("Take the bins out")).not_to_be_visible()

    # Ana completes the item her screen still shows as open.
    page.get_by_role("button", name="Complete Take the bins out", exact=True).click()

    expect(page.get_by_text(f"{passwordless_member.display_name} completed this first")
           ).to_be_visible()
    # And her screen now agrees with the server rather than with her click.
    page.get_by_role("button", name="Done").click()
    expect(page.get_by_text("Take the bins out")).to_be_visible()


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
    second_member_page.get_by_role("button", name="Done").click()
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
    points = page.get_by_role("heading", name="Points").locator("xpath=../..")
    expect(points.get_by_text("50")).to_be_visible()

    page.get_by_role("button", name="Done").click()
    page.get_by_role("button", name="Reopen Worth something").click()

    # Back on the to-do list, and the points are gone with it.
    page.get_by_role("button", name="To do").click()
    expect(page.get_by_text("Worth something")).to_be_visible()
    expect(points.get_by_text("50")).not_to_be_visible()


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
