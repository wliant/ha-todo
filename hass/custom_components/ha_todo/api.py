"""The HTTP client, and the cookie that is easy to lose.

Signs in as an ordinary household member and keeps the session, which is the
whole of adr/0057. Imports nothing from ``homeassistant`` (adr/0058), so the
retry rules and the error translation below are testable with aiohttp alone.

The flow is the one the SPA uses and the one ``e2e/conftest.py`` already proves
in Python: GET the CSRF token, POST the login echoing it, keep the JSESSIONID.
That file is a reference and not a dependency.
"""

from __future__ import annotations

import asyncio
import logging
from datetime import date
from typing import Any

import aiohttp
from yarl import URL

from .models import (
    CollectionSummary,
    Item,
    ListSummary,
    Session,
    TrackerReadings,
    TrackerSummary,
)

_LOGGER = logging.getLogger(__name__)

SESSION_COOKIE = "JSESSIONID"
CSRF_HEADER = "X-XSRF-TOKEN"
REQUEST_TIMEOUT = 20


class HaTodoError(Exception):
    """Anything the integration could not do."""


class HaTodoAuthError(HaTodoError):
    """The credentials are wrong, or the session could not be established."""


class HaTodoNoSession(HaTodoAuthError):
    """Login answered 200 and no session cookie was kept.

    Its own class because it has a cause and a fix that ``invalid_auth`` does
    not, and because it otherwise presents as an endless 401 loop against a
    password that is correct. See :meth:`HaTodoClient.login`.
    """


class HaTodoNotFound(HaTodoError):
    """No grant, or gone.

    The product answers 404 rather than 403 so a list nobody shared is not
    discoverable (adr/0010). Never flatten this into "the list is empty" - a
    revoked grant would then look like somebody finishing their chores.
    """


class HaTodoInvalid(HaTodoError):
    """The product refused the write, in its own words.

    Carries the server's sentence verbatim. Every error body in this product is
    ``{"error": "<prose written for a member>"}``, and R193 is that the member
    sees it: "This list needs every item assigned." is a sentence somebody can
    act on, and a generic failure is not.
    """


class HaTodoStale(HaTodoError):
    """Somebody else wrote first; carries the item as it actually is."""

    def __init__(self, message: str, item: dict[str, Any] | None) -> None:
        super().__init__(message)
        self.item = item


class HaTodoClient:
    """One signed-in member's view of the product."""

    def __init__(
        self,
        base_url: str,
        username: str,
        password: str,
        *,
        verify_ssl: bool = True,
    ) -> None:
        self._base_url = base_url.rstrip("/")
        self._username = username
        self._password = password
        self._verify_ssl = verify_ssl
        self._session: aiohttp.ClientSession | None = None
        self._login_lock = asyncio.Lock()
        self.identity: Session | None = None

    # -- lifecycle ---------------------------------------------------------

    def _ensure_session(self) -> aiohttp.ClientSession:
        if self._session is None or self._session.closed:
            self._session = aiohttp.ClientSession(
                # unsafe=True is load-bearing, not a shortcut. aiohttp's
                # default jar silently refuses to store cookies for a bare
                # IP-address host, and a household's deployment is very often
                # http://192.168.x.x:5987. Without it the login returns 200,
                # no cookie is kept, and every call afterwards is a 401 that
                # the re-login path below would retry forever - presenting as a
                # wrong password that is in fact correct.
                cookie_jar=aiohttp.CookieJar(unsafe=True),
                timeout=aiohttp.ClientTimeout(total=REQUEST_TIMEOUT),
                connector=aiohttp.TCPConnector(ssl=None if self._verify_ssl else False),
            )
        return self._session

    async def close(self) -> None:
        if self._session is not None and not self._session.closed:
            await self._session.close()
        self._session = None

    def _url(self, path: str) -> str:
        return f"{self._base_url}{path}"

    def _has_session_cookie(self) -> bool:
        """Whether a session cookie would actually be **sent** to the base URL.

        Deliberately ``filter_cookies`` and not "is it anywhere in the jar".
        aiohttp *stores* a ``Secure`` cookie received over http and then
        refuses to send it back, so the jar holds a ``JSESSIONID`` that can
        never be used - which is precisely the state that otherwise presents as
        an endless 401 against a password that is correct. Both deployed
        environments set ``SESSION_COOKIE_SECURE=true``, so an address typed as
        ``http://`` is the likeliest setup mistake there is.
        """
        session = self._ensure_session()
        sendable = session.cookie_jar.filter_cookies(URL(self._base_url))
        return SESSION_COOKIE in sendable

    # -- signing in --------------------------------------------------------

    async def _csrf_token(self) -> str:
        session = self._ensure_session()
        async with session.get(self._url("/api/auth/csrf")) as response:
            response.raise_for_status()
            body = await response.json()
        return body["token"]

    async def login(self) -> Session:
        """Signs in, and proves it.

        Serialised: several entities can notice a 401 in the same tick, and
        three simultaneous logins would rotate the session id out from under
        each other.
        """
        async with self._login_lock:
            session = self._ensure_session()
            session.cookie_jar.clear()
            token = await self._csrf_token()
            payload = {"username": self._username}
            if self._password:
                payload["password"] = self._password
            async with session.post(
                self._url("/api/auth/login"),
                json=payload,
                headers={CSRF_HEADER: token},
            ) as response:
                if response.status == 401:
                    raise HaTodoAuthError("That username and password were refused.")
                response.raise_for_status()
                body = await response.json()

            # A 200 whose cookie cannot be sent back is two unrelated
            # misconfigurations wearing one face: a plain http:// address
            # against a deployment that marks the cookie Secure (both dev and
            # prod do), and the IP-address jar problem above. Both otherwise
            # present as an endless 401 loop, so this check is what names the
            # cause - and it has to ask whether the cookie is *sendable*, not
            # whether it was stored. See _has_session_cookie.
            if not self._has_session_cookie():
                raise HaTodoNoSession(
                    "Signed in, but the server's session cookie cannot be used "
                    "with this address. It is marked Secure, so it is only ever "
                    "sent over https - use the https:// address rather than "
                    "http://."
                )

            self.identity = Session(
                user_id=body["id"],
                username=body["username"],
                display_name=body["displayName"],
                timezone=body["timezone"],
            )
            return self.identity

    @property
    def timezone(self) -> str:
        """The household's zone. Never Home Assistant's own (adr/0036)."""
        if self.identity is None:
            raise HaTodoError("Not signed in yet.")
        return self.identity.timezone

    # -- requests ----------------------------------------------------------

    async def _send(
        self, method: str, path: str, json: dict[str, Any] | None
    ) -> Any:
        session = self._ensure_session()
        headers: dict[str, str] = {}
        if method != "GET":
            headers[CSRF_HEADER] = await self._csrf_token()
        async with session.request(
            method, self._url(path), json=json, headers=headers
        ) as response:
            if response.status == 204:
                return None
            if response.status < 300:
                return await response.json()
            body: dict[str, Any] = {}
            try:
                body = await response.json()
            except (aiohttp.ContentTypeError, ValueError):
                pass
            return _Refusal(response.status, body)

    async def request(
        self, method: str, path: str, json: dict[str, Any] | None = None
    ) -> Any:
        """One call, with the two retries that are allowed and no others."""
        if not self._has_session_cookie():
            await self.login()

        result = await self._send(method, path, json)
        if isinstance(result, _Refusal) and result.status in (401, 403):
            # 401: the backend restarted and the session went with it, which is
            # a normal event rather than a failure - sessions are in memory, so
            # every deploy signs the household's automation out (R200). 403: the
            # CSRF token went stale alongside it. Either way, once.
            if result.status == 401:
                _LOGGER.debug("Session expired; signing in again")
                await self.login()
            result = await self._send(method, path, json)

        if isinstance(result, _Refusal):
            raise result.as_error()
        return result

    async def get(self, path: str) -> Any:
        return await self.request("GET", path)

    # -- reads -------------------------------------------------------------

    async def lists(self) -> list[ListSummary]:
        rows = await self.get("/api/lists")
        return [
            ListSummary(
                id=row["id"],
                name=row["name"],
                owned=row["owned"],
                description=row.get("description") or None,
            )
            for row in rows
        ]

    async def items(
        self, list_id: str, fields: dict[str, Any] | None = None
    ) -> list[Item]:
        rows = await self.get(f"/api/lists/{list_id}/items")
        return [Item.from_json(row, fields) for row in rows]

    async def fields(self, list_id: str) -> dict[str, Any]:
        """A list's declared fields, keyed by id.

        Fetched so an answered cost can be published with the label its list
        gave it, and so a screen can know what the list insists on before
        offering to add to it. Cached by the coordinator rather than re-read
        every poll: a field configuration changes when an owner edits it, which
        is rare, and this costs one request per list.
        """
        rows = await self.get(f"/api/lists/{list_id}/fields")
        return {row["id"]: row for row in rows}

    async def collections(self) -> list[CollectionSummary]:
        rows = await self.get("/api/data-lists")
        return [CollectionSummary(id=row["id"], name=row["name"]) for row in rows]

    async def trackers(self) -> list[TrackerSummary]:
        rows = await self.get("/api/trackers")
        return [TrackerSummary.from_json(row) for row in rows]

    async def series(
        self, tracker_id: str, start: date, end: date, by: str
    ) -> TrackerReadings:
        payload = await self.get(
            f"/api/trackers/{tracker_id}/series"
            f"?from={start.isoformat()}&to={end.isoformat()}&by={by}"
        )
        return TrackerReadings.from_json(payload)

    async def pending_trackers(self) -> list[dict[str, Any]]:
        return await self.get("/api/trackers/pending")

    async def overdue_total(self) -> int:
        """How much is late.

        ``total``, never ``len(items)``: the page is capped, so counting the
        rows under-reports, and the undercount grows with the backlog - which
        is exactly the household worth automating against (R197).
        """
        payload = await self.get("/api/dashboard/overdue?scope=all")
        return int(payload.get("total") or 0)

    # -- writes ------------------------------------------------------------

    async def create_item(
        self, list_id: str, description: str, due_at: str | None
    ) -> Item:
        """Adds a chore.

        ``detailHtml`` is deliberately absent and must stay absent: see
        adr/0061. ``version`` is null when creating.
        """
        payload: dict[str, Any] = {"description": description}
        if due_at is not None:
            payload["dueAt"] = due_at
        return Item.from_json(
            await self.request("POST", f"/api/lists/{list_id}/items", payload)
        )

    async def update_item(
        self, item_id: str, version: int, description: str, due_at: str | None
    ) -> Item:
        payload: dict[str, Any] = {
            "version": version,
            "description": description,
            "dueAt": due_at,
        }
        return Item.from_json(
            await self.request("PATCH", f"/api/items/{item_id}", payload)
        )

    async def set_item_state(self, item_id: str, version: int, state: str) -> Item:
        """Asks an item to become ``OPEN``, ``COMPLETED`` or ``LAPSED``.

        No ``noteHtml``: it is only ever legal beside ``COMPLETED``, it is rich
        text (adr/0061), and an automation has nothing to say about how the
        work went.
        """
        return Item.from_json(
            await self.request(
                "PUT",
                f"/api/items/{item_id}/state",
                {"version": version, "state": state},
            )
        )

    async def add_entry(
        self,
        collection_id: str,
        name: str,
        description: str | None,
        tags: list[str] | None,
    ) -> dict[str, Any]:
        payload: dict[str, Any] = {"name": name}
        if description:
            payload["description"] = description
        if tags:
            payload["tags"] = tags
        return await self.request(
            "POST", f"/api/data-lists/{collection_id}/entries", payload
        )

    async def record_reading(
        self,
        tracker_id: str,
        amount: float | str,
        period_start: date,
        note: str | None,
    ) -> dict[str, Any]:
        """Records a number.

        ``periodStart`` is sent as the household's civil date and the **server**
        buckets it. Nothing here reimplements ``TrackerPeriod.startOf``.
        """
        payload: dict[str, Any] = {
            "periodStart": period_start.isoformat(),
            "amount": str(amount),
        }
        if note:
            payload["note"] = note
        return await self.request(
            "POST", f"/api/trackers/{tracker_id}/readings", payload
        )


class _Refusal:
    """A non-2xx, held rather than raised so the retry rules can see it."""

    __slots__ = ("status", "body")

    def __init__(self, status: int, body: dict[str, Any]) -> None:
        self.status = status
        self.body = body

    def as_error(self) -> HaTodoError:
        message = self.body.get("error") or f"The server answered {self.status}."
        if self.status in (401, 403):
            return HaTodoAuthError(message)
        if self.status == 404:
            return HaTodoNotFound(message)
        if self.status == 409:
            return HaTodoStale(message, self.body.get("item"))
        if self.status == 400:
            return HaTodoInvalid(message)
        return HaTodoError(message)
