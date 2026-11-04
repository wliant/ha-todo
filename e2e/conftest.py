"""Shared fixtures: environment targeting, an API client, and test-data hygiene."""

from __future__ import annotations

import os
import secrets
import time
from collections.abc import Callable, Iterator
from dataclasses import dataclass, field

import httpx
import pytest

DEFAULT_BASE_URL = "http://localhost:5173"


@pytest.fixture(scope="session")
def base_url() -> str:
    """Overrides pytest-base-url so the target comes from the environment."""
    return os.environ.get("E2E_BASE_URL", DEFAULT_BASE_URL).rstrip("/")


@pytest.fixture(scope="session")
def demo_credentials() -> tuple[str, str]:
    return (
        os.environ.get("E2E_EMAIL", "ana@example.com"),
        os.environ.get("E2E_PASSWORD", "password123"),
    )


@pytest.fixture(scope="session")
def api(base_url: str) -> Iterator[httpx.Client]:
    """Cookie-persisting API client, used for setup and teardown rather than UI."""
    with httpx.Client(base_url=base_url, timeout=20.0, follow_redirects=True) as client:
        yield client


@pytest.fixture(scope="session")
def authenticated_api(api: httpx.Client, demo_credentials: tuple[str, str]) -> httpx.Client:
    """An API client holding a real session, for seeding data the UI then reads."""
    email, password = demo_credentials
    csrf = api.get("/api/auth/csrf")
    csrf.raise_for_status()
    token = csrf.json()["token"]

    response = api.post(
        "/api/auth/login",
        json={"email": email, "password": password},
        headers={"X-XSRF-TOKEN": token},
    )
    response.raise_for_status()
    return api


@dataclass
class TestData:
    """
    Namespaces everything a run creates and remembers how to remove it.

    The suite runs against a shared dev environment, so names must not collide
    between concurrent or repeated runs, and nothing may survive the run.
    """

    prefix: str
    _cleanups: list[Callable[[], None]] = field(default_factory=list)

    def name(self, label: str) -> str:
        """A collision-proof, obviously-synthetic name for a created resource."""
        return f"{self.prefix}-{label}"

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
