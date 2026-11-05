"""
Collections: keeping what the household looks up, and finding it again
(requirements/0006 R50-R61).

Labels are chosen per *module*, not per concept: ``test_data`` is session
scoped, so two modules both asking for ``name("Recipes")`` get the same string
and the second create fails on the per-owner unique index.
"""

from __future__ import annotations

import pytest
from playwright.sync_api import Page, expect

from conftest import DemoMember, TestData, ui_sign_in


def go_to_collections(page: Page) -> None:
    """Signing in lands on the dashboard (R44), so get to the menu entry first."""
    page.get_by_role("link", name="Collections").first.click()


def make_collection(page: Page, name: str) -> None:
    go_to_collections(page)
    page.get_by_role("link", name="New collection").click()
    page.get_by_role("textbox", name="Name", exact=True).fill(name)
    page.get_by_role("button", name="Create", exact=True).click()
    expect(page.get_by_role("heading", name=name)).to_be_visible()


def add_entry(page: Page, name: str, detail: str | None = None, tags: list[str] | None = None) -> None:
    page.get_by_role("button", name="New entry").click()
    page.get_by_role("textbox", name="Name", exact=True).fill(name)
    if tags:
        for tag in tags:
            page.get_by_role("combobox", name="Tags").fill(tag)
            page.get_by_role("combobox", name="Tags").press("Enter")
    if detail:
        # The editor is lazy-loaded, so it may not be there the instant the
        # dialog opens.
        editor = page.get_by_role("textbox", name="Detail")
        expect(editor).to_be_visible()
        editor.fill(detail)
    page.get_by_role("button", name="Add", exact=True).click()
    expect(page.get_by_text(name)).to_be_visible()


@pytest.mark.collections
def test_member_keeps_a_recipe_and_reads_it_back(
    page: Page,
    base_url: str,
    demo_member: DemoMember,
    test_data: TestData,
    cleanup_collections,
) -> None:
    name = test_data.name("CollRecipes")
    cleanup_collections(name)
    ui_sign_in(page, base_url, demo_member)

    make_collection(page, name)
    add_entry(page, "Tiramisu", detail="savoiardi and espresso", tags=["italian"])

    # Collapsed shows the name and the tags; the detail is one tap away (R57).
    expect(page.get_by_text("italian").first).to_be_visible()
    expect(page.get_by_text("savoiardi and espresso")).to_have_count(0)

    page.get_by_role("button", name="Open Tiramisu").click()
    expect(page.get_by_text("savoiardi and espresso")).to_be_visible()


@pytest.mark.collections
def test_search_finds_a_word_that_is_only_in_the_detail(
    page: Page,
    base_url: str,
    demo_member: DemoMember,
    test_data: TestData,
    cleanup_collections,
) -> None:
    """
    The point of the whole feature: the ingredient three bullets down is what a
    member actually remembers, and the title is what they have forgotten.
    """
    name = test_data.name("CollSearch")
    cleanup_collections(name)
    ui_sign_in(page, base_url, demo_member)

    make_collection(page, name)
    add_entry(page, "Nonna's dish", detail="guanciale, pecorino and black pepper")
    add_entry(page, "Something else", detail="nothing relevant")

    search = page.get_by_role("textbox", name="Search this collection")
    search.fill("guanciale")
    expect(page.get_by_text("Nonna's dish")).to_be_visible()
    expect(page.get_by_text("Something else")).to_have_count(0)

    # And a word that is in neither.
    search.fill("zzzznothing")
    expect(page.get_by_text("Nothing matches that.")).to_be_visible()


@pytest.mark.collections
def test_global_search_says_which_collection_a_hit_came_from(
    page: Page,
    base_url: str,
    demo_member: DemoMember,
    test_data: TestData,
    cleanup_collections,
) -> None:
    """R54: one box answers "where did I write that down"."""
    recipes = test_data.name("CollGlobalA")
    words = test_data.name("CollGlobalB")
    cleanup_collections(recipes)
    cleanup_collections(words)
    ui_sign_in(page, base_url, demo_member)

    make_collection(page, recipes)
    add_entry(page, "Zabaglione")
    make_collection(page, words)
    add_entry(page, "Zabaglione the word")

    go_to_collections(page)
    page.get_by_role("textbox", name="Search everything").fill("zabaglione")

    expect(page.get_by_text("Zabaglione", exact=True)).to_be_visible()
    expect(page.get_by_role("link", name=recipes)).to_be_visible()
    expect(page.get_by_role("link", name=words)).to_be_visible()


@pytest.mark.collections
def test_search_finds_a_word_by_its_middle(
    page: Page,
    base_url: str,
    demo_member: DemoMember,
    test_data: TestData,
    cleanup_collections,
) -> None:
    """R55: a vocabulary collection is only useful if the spelling is optional."""
    name = test_data.name("CollVocab")
    cleanup_collections(name)
    ui_sign_in(page, base_url, demo_member)

    make_collection(page, name)
    add_entry(page, "Avian")

    search = page.get_by_role("textbox", name="Search this collection")
    # The prefix, which full text gives for free.
    search.fill("avi")
    expect(page.get_by_text("Avian")).to_be_visible()
    # And the middle, which only the trigram arm can answer.
    search.fill("vian")
    expect(page.get_by_text("Avian")).to_be_visible()


@pytest.mark.collections
def test_tag_filters_narrow_rather_than_widen(
    page: Page,
    base_url: str,
    demo_member: DemoMember,
    test_data: TestData,
    cleanup_collections,
) -> None:
    """R56: "thai and quick", never "thai or quick"."""
    name = test_data.name("CollTags")
    cleanup_collections(name)
    ui_sign_in(page, base_url, demo_member)

    make_collection(page, name)
    add_entry(page, "Both of them", tags=["italian", "quick"])
    add_entry(page, "Only quick one", tags=["quick"])

    page.get_by_role("button", name="quick (2)").click()
    expect(page.get_by_text("Both of them")).to_be_visible()
    expect(page.get_by_text("Only quick one")).to_be_visible()

    page.get_by_role("button", name="italian (1)").click()
    expect(page.get_by_text("Both of them")).to_be_visible()
    expect(page.get_by_text("Only quick one")).to_have_count(0)


@pytest.mark.collections
def test_a_collection_is_not_a_list_and_asks_for_no_due_date(
    page: Page,
    base_url: str,
    demo_member: DemoMember,
    test_data: TestData,
    cleanup_collections,
) -> None:
    """
    R50's whole point, from the member's side: nothing here asks when it is due
    or offers to complete it.
    """
    name = test_data.name("CollNotWork")
    cleanup_collections(name)
    ui_sign_in(page, base_url, demo_member)

    make_collection(page, name)
    page.get_by_role("button", name="New entry").click()

    expect(page.get_by_role("textbox", name="Name", exact=True)).to_be_visible()
    expect(page.get_by_label("Due")).to_have_count(0)
    expect(page.get_by_role("button", name="Complete")).to_have_count(0)


@pytest.mark.collections
def test_two_members_editing_one_entry_are_told_rather_than_losing_it(
    page: Page,
    second_member_page: Page,
    base_url: str,
    demo_member: DemoMember,
    passwordless_member: DemoMember,
    test_data: TestData,
    cleanup_collections,
    authenticated_api,
) -> None:
    """
    R60, and the reason this suite exists: two people on the same entry at
    once. The second writer is shown what it actually says now, and nothing is
    retried behind their back.
    """
    name = test_data.name("CollShared")
    cleanup_collections(name)

    # Seeded through the API so both browsers start from the same state, and so
    # the grant exists before either of them looks.
    import conftest

    members = authenticated_api.get("/api/users")
    members.raise_for_status()
    other = next(
        member
        for member in members.json()
        if member["displayName"] == passwordless_member.display_name
    )
    created = authenticated_api.post(
        "/api/data-lists",
        json={"name": name, "icon": "GROCERIES", "memberIds": [other["id"]]},
        headers=conftest.api_csrf_header(authenticated_api),
    )
    created.raise_for_status()
    collection_id = created.json()["id"]
    entry = authenticated_api.post(
        f"/api/data-lists/{collection_id}/entries",
        json={"name": "Contended entry"},
        headers=conftest.api_csrf_header(authenticated_api),
    )
    entry.raise_for_status()

    ui_sign_in(page, base_url, demo_member)
    ui_sign_in(second_member_page, base_url, passwordless_member)
    page.goto(f"{base_url}/collections/{collection_id}")
    second_member_page.goto(f"{base_url}/collections/{collection_id}")

    # Both open the same entry for editing, from the same version.
    page.get_by_role("button", name="Edit Contended entry").click()
    second_member_page.get_by_role("button", name="Edit Contended entry").click()

    page.get_by_role("textbox", name="Name", exact=True).fill("First writer wins")
    page.get_by_role("button", name="Save", exact=True).click()
    expect(page.get_by_text("First writer wins")).to_be_visible()

    second_member_page.get_by_role("textbox", name="Name", exact=True).fill("Second writer")
    second_member_page.get_by_role("button", name="Save", exact=True).click()

    # Told, with the server's own wording, and the dialog stays open.
    expect(second_member_page.get_by_text("Someone else changed this entry")).to_be_visible()
