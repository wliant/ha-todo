"""Shared fixtures: environment targeting, an API client, and test-data hygiene."""

from __future__ import annotations

import os
import secrets
import time
import re
from collections.abc import Callable, Iterator
from dataclasses import dataclass, field

import httpx
import pytest

DEFAULT_BASE_URL = "http://localhost:5173"


@pytest.fixture(scope="session")
def base_url() -> str:
    """Overrides pytest-base-url so the target comes from the environment."""
    return os.environ.get("E2E_BASE_URL", DEFAULT_BASE_URL).rstrip("/")


@dataclass(frozen=True)
class DemoMember:
    """A seeded account. ``password`` of None means none is required."""

    username: str
    display_name: str
    password: str | None


@pytest.fixture(scope="session")
def demo_member() -> DemoMember:
    """Ana: a member who signs in with a password."""
    return DemoMember(
        username=os.environ.get("E2E_USERNAME", "ana"),
        display_name=os.environ.get("E2E_DISPLAY_NAME", "Ana"),
        password=os.environ.get("E2E_PASSWORD", "password123"),
    )


@pytest.fixture(scope="session")
def passwordless_member() -> DemoMember:
    """Ben: a member who signs in by being picked, with no password at all."""
    return DemoMember(
        username=os.environ.get("E2E_PASSWORDLESS_USERNAME", "ben"),
        display_name=os.environ.get("E2E_PASSWORDLESS_DISPLAY_NAME", "Ben"),
        password=None,
    )


@pytest.fixture(scope="session")
def admin_member() -> DemoMember:
    """
    The admin, whose credentials come from the environment the app was deployed
    with (docs/adr/0006). Tests that need it skip when it is not supplied,
    rather than guessing a password.
    """
    password = os.environ.get("E2E_ADMIN_PASSWORD")
    if not password:
        pytest.skip("set E2E_ADMIN_PASSWORD to exercise user administration")
    return DemoMember(
        username=os.environ.get("E2E_ADMIN_USERNAME", "admin"),
        display_name=os.environ.get("E2E_ADMIN_DISPLAY_NAME", "Admin"),
        password=password,
    )


@pytest.fixture(scope="session")
def api(base_url: str) -> Iterator[httpx.Client]:
    """Cookie-persisting API client, used for setup and teardown rather than UI."""
    with httpx.Client(base_url=base_url, timeout=20.0, follow_redirects=True) as client:
        yield client


def api_sign_in(client: httpx.Client, member: DemoMember) -> httpx.Client:
    """Signs ``client`` in, carrying the CSRF token the login POST requires."""
    csrf = client.get("/api/auth/csrf")
    csrf.raise_for_status()
    token = csrf.json()["token"]

    payload: dict[str, str] = {"username": member.username}
    if member.password is not None:
        payload["password"] = member.password

    response = client.post(
        "/api/auth/login", json=payload, headers={"X-XSRF-TOKEN": token}
    )
    response.raise_for_status()
    return client


def api_csrf_header(client: httpx.Client) -> dict[str, str]:
    csrf = client.get("/api/auth/csrf")
    csrf.raise_for_status()
    return {"X-XSRF-TOKEN": csrf.json()["token"]}


@pytest.fixture(scope="session")
def authenticated_api(api: httpx.Client, demo_member: DemoMember) -> httpx.Client:
    """An API client holding a real session, for seeding data the UI then reads."""
    return api_sign_in(api, demo_member)


@pytest.fixture(scope="session")
def admin_api(base_url: str, admin_member: DemoMember) -> Iterator[httpx.Client]:
    """
    A separate admin session, for seeding and removing accounts.

    Session-scoped on purpose: deferred cleanup registered with ``test_data``
    runs at the end of the session, so a client that closed with the test would
    leave the accounts it created behind.
    """
    with httpx.Client(base_url=base_url, timeout=20.0, follow_redirects=True) as client:
        yield api_sign_in(client, admin_member)


@pytest.fixture
def cleanup_user(base_url: str, admin_member: DemoMember, test_data: TestData):
    """
    Registers a login name to be deleted at the end of the session.

    Opens its own client when it runs, for the same reason cleanup_lists does.
    """

    def remove(username: str) -> None:
        def delete() -> None:
            with httpx.Client(
                base_url=base_url, timeout=20.0, follow_redirects=True
            ) as client:
                api_sign_in(client, admin_member)
                users = client.get("/api/admin/users")
                users.raise_for_status()
                for user in users.json():
                    if user["username"] == username:
                        response = client.delete(
                            f"/api/admin/users/{user['id']}",
                            headers=api_csrf_header(client),
                        )
                        response.raise_for_status()

        test_data.defer_cleanup(delete)

    return remove


@pytest.fixture
def cleanup_lists(base_url: str, demo_member: DemoMember, test_data: TestData):
    """
    Registers a list, by name, to be deleted at the end of the session.

    The deferred action opens its own client when it runs, rather than closing
    over a fixture's. Session fixtures are finalized in reverse order of
    creation, so a client held here could already be closed by the time cleanup
    needs it - which fails silently as "dev may be polluted" rather than as
    anything that points at the cause.

    By name rather than by id because the test usually creates the list through
    the UI and never learns its id - and a name from ``test_data`` is unique to
    the run, so this can never reach somebody else's list.
    """

    def remove(name: str) -> None:
        def delete() -> None:
            with httpx.Client(
                base_url=base_url, timeout=20.0, follow_redirects=True
            ) as client:
                api_sign_in(client, demo_member)
                found = client.get("/api/lists", params={"q": name})
                found.raise_for_status()
                for entry in found.json():
                    response = client.delete(
                        f"/api/lists/{entry['id']}", headers=api_csrf_header(client)
                    )
                    response.raise_for_status()

        test_data.defer_cleanup(delete)

    return remove


@pytest.fixture
def cleanup_collections(base_url: str, demo_member: DemoMember, test_data: TestData):
    """
    Registers a collection, by name, to be deleted at the end of the session
    (requirements/0006).

    The same shape as :func:`cleanup_lists`, and separate from it for the same
    reason the backend keeps the two apart: a collection is not a list, and
    deleting one through the other's endpoint would answer 404. Deleting a
    collection takes its entries and their pictures with it, so nothing else
    needs registering.
    """

    def remove(name: str) -> None:
        def delete() -> None:
            with httpx.Client(
                base_url=base_url, timeout=20.0, follow_redirects=True
            ) as client:
                api_sign_in(client, demo_member)
                found = client.get("/api/data-lists", params={"q": name})
                found.raise_for_status()
                for entry in found.json():
                    response = client.delete(
                        f"/api/data-lists/{entry['id']}", headers=api_csrf_header(client)
                    )
                    response.raise_for_status()

        test_data.defer_cleanup(delete)

    return remove


def pick_user(page, member: DemoMember) -> None:
    """Choose a person on the sign-in screen, the way a family member does."""
    page.get_by_role("button", name=member.display_name).click()


def profile_link(page, member: DemoMember):
    """
    The sidebar row that names the signed-in member (R15).

    Matched on the row's whole accessible name: a bare display name also
    matches any list whose title contains it, which the demo data alone is
    enough to trip over.
    """
    return page.get_by_role("link", name=f"{member.display_name} Your profile")


def ui_sign_in(page, base_url: str, member: DemoMember) -> None:
    """
    Sign in through the real screen: pick the person, then type a password only
    if that person needs one.

    Waits for the signed-in page before returning. Without that, a caller that
    navigates straight afterwards races the login request and arrives back at
    the sign-in screen.
    """
    from playwright.sync_api import expect

    page.goto(f"{base_url}/login")
    pick_user(page, member)
    if member.password is not None:
        page.get_by_label("Password").fill(member.password)
        page.get_by_role("button", name="Sign in", exact=True).click()
    # Asserts being signed in, not being anywhere in particular. A fresh
    # sign-in lands on the dashboard (R44), but ProtectedRoute remembers the
    # screen a member was sent away from - so signing in again resumes there,
    # and waiting on one page's heading makes this fixture wrong for half its
    # callers. Leaving the sign-in screen is the thing every caller means.
    expect(page.get_by_role("heading", name="Who's signing in?")).to_have_count(0)
    expect(page).not_to_have_url(re.compile(r"/login"))


@dataclass
class TestData:
    """
    Namespaces everything a run creates and remembers how to remove it.

    The suite runs against a shared dev environment, so names must not collide
    between concurrent or repeated runs, and nothing may survive the run.
    """

    # Importing this into a test module would otherwise make pytest try to
    # collect it as a test class, on the name alone.
    __test__ = False

    prefix: str
    _cleanups: list[Callable[[], None]] = field(default_factory=list)

    def name(self, label: str) -> str:
        """A collision-proof, obviously-synthetic name for a created resource."""
        return f"{self.prefix}-{label}"

    def username(self, label: str) -> str:
        """
        Like :meth:`name`, but within the 50 characters and restricted alphabet
        a login name allows.
        """
        return f"e2e-{label}-{secrets.token_hex(4)}"

    def defer_cleanup(self, cleanup: Callable[[], None]) -> None:
        self._cleanups.append(cleanup)

    def cleanup(self) -> list[Exception]:
        failures: list[Exception] = []
        for action in reversed(self._cleanups):
            try:
                action()
            except Exception as error:  # keep going; one leak must not hide others
                failures.append(error)
        self._cleanups.clear()
        return failures


@pytest.fixture(scope="session")
def test_data() -> Iterator[TestData]:
    data = TestData(prefix=f"e2e-{int(time.time())}-{secrets.token_hex(3)}")
    yield data
    failures = data.cleanup()
    if failures:
        raise AssertionError(f"test data cleanup failed, dev may be polluted: {failures}")


@pytest.fixture
def second_member_page(browser, base_url: str):
    """
    A second browser context, i.e. a second family member with their own session.

    Concurrent edits by two members are the behaviour this suite exists to cover,
    so the harness for it belongs here from the start.
    """
    context = browser.new_context(base_url=base_url)
    page = context.new_page()
    yield page
    context.close()
