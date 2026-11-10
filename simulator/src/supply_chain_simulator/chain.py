"""Minimalny klient JSON-RPC do odczytu stanu z kontraktu (DEC-10).

Symulator czyta z łańcucha wyłącznie stan partii (`getBatch`) i stacji
(`getStation`) publicznym `eth_call`; nie podpisuje transakcji i ich nie wysyła
(DEC-10, `docs/decisions.md`). Kodowanie i dekodowanie ABI robi `eth_abi`,
selektor liczymy Keccakiem - to ten sam kontrakt co w `contracts/`
(`shared/abi/SupplyChain.abi.json`).

Klient HTTP jest wstrzykiwany, więc testy używają `httpx.MockTransport` i nie
wymagają sieci ani Anvila. Nazwy wyjść (`exists`, `nextStage`, `completed`,
`active`, `stationId`, `expiresAt`, `stageMask`) pochodzą z ABI.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Any

import httpx
from eth_abi import decode as abi_decode
from eth_abi import encode as abi_encode
from eth_utils import keccak

from supply_chain_simulator.models import normalize_address

DEFAULT_RPC_TIMEOUT_SECONDS = 10.0
HEX_32_PATTERN = r"^0x[0-9a-fA-F]{64}$"


class ChainError(RuntimeError):
    """Odczyt z łańcucha nie udał się: transport, błąd RPC albo zła odpowiedź."""


@dataclass(frozen=True)
class BatchState:
    """Stan partii z `getBatch`: czy istnieje, oczekiwany etap i ukończenie."""

    exists: bool
    next_stage: int
    completed: bool


@dataclass(frozen=True)
class StationState:
    """Stan rejestracji stacji z `getStation`."""

    active: bool
    station_id: str
    expires_at: int
    stage_mask: int


def function_selector(signature: str) -> bytes:
    """Pierwsze 4 bajty Keccaka sygnatury funkcji (np. `getBatch(bytes32)`)."""
    return keccak(text=signature)[:4]


def encode_call(signature: str, arg_types: list[str], args: list[Any]) -> str:
    """Koduje `eth_call`: selektor + argumenty ABI jako `0x...`."""
    return "0x" + (function_selector(signature) + abi_encode(arg_types, args)).hex()


def _require_hex32(value: object, field: str) -> str:
    if not isinstance(value, str) or not re.fullmatch(HEX_32_PATTERN, value):
        raise ValueError(f"{field} musi być 0x + 64 znaki hex")
    return value


class ChainReader:
    """Synchroniczny czytnik `eth_call` dla jednego kontraktu.

    ``client`` i ``request_id`` wstrzykujemy w testach. Klienta wstrzykniętego
    nie zamyka ``close()`` - zamykamy tylko ten, który sami utworzyliśmy.
    """

    def __init__(
        self,
        rpc_url: str,
        contract: str,
        *,
        client: httpx.Client | None = None,
        timeout: float = DEFAULT_RPC_TIMEOUT_SECONDS,
        request_id: int = 1,
    ) -> None:
        if (
            not isinstance(rpc_url, str)
            or not rpc_url.startswith(("http://", "https://"))
            or rpc_url.rstrip("/") in {"http:", "https:"}
        ):
            raise ValueError("rpc_url musi być adresem http(s)://")
        if timeout <= 0:
            raise ValueError("timeout musi być dodatni")
        try:
            self._contract = normalize_address(contract)
        except ValueError as exc:
            raise ValueError(f"adres kontraktu niepoprawny: {exc}") from exc
        self._rpc_url = rpc_url.rstrip("/")
        self._timeout = timeout
        self._request_id = request_id
        self._owns_client = client is None
        self._client = client if client is not None else httpx.Client(timeout=timeout)

    @property
    def contract(self) -> str:
        """Adres kontraktu małymi literami."""
        return self._contract

    def get_batch(self, batch_id: str) -> BatchState:
        """Odczytuje `getBatch(batchId)` -> `(exists, nextStage, completed)`."""
        data = encode_call(
            "getBatch(bytes32)",
            ["bytes32"],
            [bytes.fromhex(_require_hex32(batch_id, "batchId")[2:])],
        )
        exists, next_stage, completed = abi_decode(
            ["bool", "uint32", "bool"], self._eth_call(data)
        )
        return BatchState(bool(exists), int(next_stage), bool(completed))

    def get_station(self, address: str) -> StationState:
        """Odczytuje `getStation(station)` -> `(active, stationId, expiresAt, stageMask)`."""
        data = encode_call("getStation(address)", ["address"], [normalize_address(address)])
        active, station_id, expires_at, stage_mask = abi_decode(
            ["bool", "bytes32", "uint64", "uint32"], self._eth_call(data)
        )
        return StationState(
            bool(active), "0x" + bytes(station_id).hex(), int(expires_at), int(stage_mask)
        )

    def _eth_call(self, data: str) -> bytes:
        """Wysyła `eth_call` i zwraca surowe bajty wyniku."""
        payload = {
            "jsonrpc": "2.0",
            "id": self._request_id,
            "method": "eth_call",
            "params": [{"to": self._contract, "data": data}, "latest"],
        }
        try:
            response = self._client.post(self._rpc_url, json=payload)
        except httpx.HTTPError as exc:
            raise ChainError(f"błąd transportu RPC: {exc}") from exc
        if response.status_code != 200:
            raise ChainError(f"RPC zwrócił HTTP {response.status_code}")
        try:
            body = response.json()
        except ValueError as exc:
            raise ChainError("RPC nie zwrócił JSON") from exc
        if not isinstance(body, dict):
            raise ChainError("nieoczekiwana odpowiedź RPC (nie obiekt JSON)")
        if body.get("error") is not None:
            raise ChainError(f"błąd RPC: {body['error']}")
        result = body.get("result")
        if not isinstance(result, str) or not result.startswith("0x"):
            raise ChainError("brak wyniku eth_call w odpowiedzi RPC")
        try:
            return bytes.fromhex(result[2:])
        except ValueError as exc:
            raise ChainError("wynik eth_call nie jest hex") from exc

    def close(self) -> None:
        """Zamyka klienta HTTP, jeśli należy do tego obiektu."""
        if self._owns_client:
            self._client.close()

    def __enter__(self) -> ChainReader:
        return self

    def __exit__(self, *exc_info: object) -> None:
        self.close()
