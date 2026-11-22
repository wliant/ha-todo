"""Signing in, staying signed in, and what each refusal means.

Against a real aiohttp server rather than a mocked transport, because two of
the things most worth pinning here are about cookies and headers - which a
mock would simply agree with.
"""

from __future__ import annotations

import pytest
from aiohttp import web

from _pure import api as api_mod

pytestmark = pytest.mark.api

HaTodoClient = api_mod.HaTodoClient


class FakeProduct:
    """Just enough of the product to exercise the client's rules."""

    def __init__(
        self, *, set_session_cookie: bool = True, secure_cookie: bool = False
    ) -> None:
        self.set_session_cookie = set_session_cookie
        self.secure_cookie = secure_cookie
        self.logins = 0
        self.calls: list[str] = []
        self.csrf_requests = 0
        self.session_valid = True
        self.item_version = 3
        self.item_state = "OPEN"
        self.next_refusals: list[tuple[int, dict]] = []

    def app(self) -> web.Application:
        app = web.Application()
        app.router.add_get("/api/auth/csrf", self.csrf)
        app.router.add_post("/api/auth/login", self.login)
        app.router.add_get("/api/lists", self.lists)
        app.router.add_put("/api/items/{id}/state", self.set_state)
        app.router.add_get("/api/dashboard/overdue", self.overdue)
        return app

    async def csrf(self, request: web.Request) -> web.Response:
        self.csrf_requests += 1
        return web.json_response({"headerName": "X-XSRF-TOKEN", "token": "tok"})

    async def login(self, request: web.Request) -> web.Response:
        body = await request.json()
        if request.headers.get("X-XSRF-TOKEN") != "tok":
            return web.json_response({"error": "no csrf"}, status=403)
        if body.get("password") != "secret":
            return web.Response(status=401)
        self.logins += 1
        self.session_valid = True
        response = web.json_response(
            {
                "id": "user-1",
                "username": "homeassistant",
                "displayName": "Home Assistant",
                "role": "MEMBER",
                "avatarImageId": None,
                "timezone": "Asia/Singapore",
            }
        )
        if self.set_session_cookie:
            response.set_cookie("JSESSIONID", "abc", secure=self.secure_cookie)
        return response

    def _guard(self, request: web.Request) -> web.Response | None:
        if not self.session_valid:
            return web.Response(status=401)
        return None

    async def lists(self, request: web.Request) -> web.Response:
        self.calls.append("lists")
        if (refused := self._guard(request)) is not None:
            return refused
        if self.next_refusals:
            status, body = self.next_refusals.pop(0)
            return web.json_response(body, status=status)
        return web.json_response(
            [{"id": "list-1", "name": "Groceries", "owned": True}]
        )

    async def set_state(self, request: web.Request) -> web.Response:
        self.calls.append("state")
        body = await request.json()
        if body["version"] != self.item_version:
            return web.json_response(
                {
                    "code": "STALE_ITEM",
                    "error": "Ana completed this first.",
                    "item": self._item(),
                },
                status=409,
            )
        self.item_state = body["state"]
        self.item_version += 1
        return web.json_response(self._item())

    async def overdue(self, request: web.Request) -> web.Response:
        # A capped page of two, against a real total of nine.
        return web.json_response(
            {"items": [self._item(), self._item()], "total": 9}
        )

    def _item(self) -> dict:
        return {
            "id": "item-1",
            "listId": "list-1",
            "description": "Milk",
            "state": self.item_state,
            "dueAt": None,
            "version": self.item_version,
        }


@pytest.fixture
async def product_and_client(aiohttp_server):
    async def _make(**kwargs):
        product = FakeProduct(**kwargs)
        server = await aiohttp_server(product.app())
        client = HaTodoClient(
            f"http://{server.host}:{server.port}", "homeassistant", "secret"
        )
        return product, client

    return _make


async def test_signing_in_carries_the_csrf_token_and_keeps_the_session(
    product_and_client,
):
    product, client = await product_and_client()
    identity = await client.login()
    assert identity.user_id == "user-1"
    # The household's zone, not the machine's - this is what everything
    # date-shaped downstream reads.
    assert identity.timezone == "Asia/Singapore"
    assert product.logins == 1
    await client.close()


async def test_a_wrong_password_is_an_auth_error_not_a_retry_loop(
    product_and_client,
):
    product, client = await product_and_client()
    client._password = "wrong"
    with pytest.raises(api_mod.HaTodoAuthError):
        await client.login()
    await client.close()


async def test_a_login_that_keeps_no_cookie_says_so_rather_than_looping(
    product_and_client,
):
    """The http-against-Secure and IP-address-jar case.

    Without this check the login succeeds, no cookie is kept, and every call
    afterwards 401s - against a password that is correct.
    """
    product, client = await product_and_client(set_session_cookie=False)
    with pytest.raises(api_mod.HaTodoNoSession):
        await client.login()
    await client.close()


async def test_a_secure_cookie_over_http_says_so_rather_than_looping(
    product_and_client,
):
    """Found by deploying, not by reasoning.

    aiohttp *stores* a Secure cookie received over http and then refuses to
    send it, so the jar holds a JSESSIONID that can never be used. Checking
    "is it in the jar" therefore passes and the whole thing presents as an
    endless 401 against a password that is correct - which is what both
    deployed environments would do, since they set SESSION_COOKIE_SECURE=true.
    """
    product, client = await product_and_client(secure_cookie=True)
    with pytest.raises(api_mod.HaTodoNoSession) as refused:
        await client.login()
    assert "https" in str(refused.value)
    # Exactly once: it must not retry its way around a setup mistake.
    assert product.logins == 1
    await client.close()


async def test_an_expired_session_is_signed_back_in_once_and_the_call_succeeds(
    product_and_client,
):
    """R200: every deploy ends the session, and that is not a failure."""
    product, client = await product_and_client()
    await client.login()
    assert await client.lists()

    product.session_valid = False  # the backend restarted
    rows = await client.lists()

    assert [row.name for row in rows] == ["Groceries"]
    assert product.logins == 2


async def test_a_second_refusal_is_not_retried_again(product_and_client):
    product, client = await product_and_client()
    await client.login()
    product.next_refusals = [(401, {}), (401, {})]
    with pytest.raises(api_mod.HaTodoAuthError):
        await client.lists()
    # One re-login, and exactly two attempts at the call itself.
    assert product.logins == 2
    assert product.calls.count("lists") == 2
    await client.close()


async def test_a_404_is_its_own_error_and_never_an_empty_list(product_and_client):
    """A revoked grant must not read as somebody finishing their chores."""
    product, client = await product_and_client()
    await client.login()
    product.next_refusals = [(404, {"error": "That item is no longer here."})]
    with pytest.raises(api_mod.HaTodoNotFound):
        await client.lists()
    await client.close()


async def test_a_refused_write_carries_the_product_s_own_sentence(
    product_and_client,
):
    product, client = await product_and_client()
    await client.login()
    product.next_refusals = [
        (400, {"error": "This list needs every item assigned."})
    ]
    with pytest.raises(api_mod.HaTodoInvalid) as refusal:
        await client.lists()
    assert str(refusal.value) == "This list needs every item assigned."
    await client.close()


async def test_a_stale_write_carries_the_item_as_it_actually_is(
    product_and_client,
):
    product, client = await product_and_client()
    await client.login()
    with pytest.raises(api_mod.HaTodoStale) as conflict:
        await client.set_item_state("item-1", 1, "COMPLETED")
    assert conflict.value.item is not None
    assert conflict.value.item["version"] == 3
    assert "Ana" in str(conflict.value)
    await client.close()


async def test_completing_with_the_version_in_hand_succeeds(product_and_client):
    product, client = await product_and_client()
    await client.login()
    item = await client.set_item_state("item-1", 3, "COMPLETED")
    assert item.state == "COMPLETED"
    await client.close()


async def test_overdue_is_the_server_s_total_not_the_page_it_sent(
    product_and_client,
):
    """The page is capped; counting its rows under-reports exactly when it matters."""
    product, client = await product_and_client()
    await client.login()
    assert await client.overdue_total() == 9
    await client.close()
