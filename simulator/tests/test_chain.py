"""Testy odczytu stanu z kontraktu przez RPC (DEC-10)."""

from __future__ import annotations

import json

import httpx
import pytest
from eth_abi import encode as abi_encode
from eth_utils import keccak
from supply_chain_simulator.chain import (
    BatchState,
    ChainError,
    ChainReader,
    encode_call,
    function_selector,
)

RPC_URL = "http://127.0.0.1:8545"
CONTRACT = "0x" + "ab" * 20
BATCH_ID = "0x" + "cd" * 32
STATION = "0x" + "11" * 20


def reader_with(handler) -> ChainReader:
    return ChainReader(
        RPC_URL, CONTRACT, client=httpx.Client(transport=httpx.MockTransport(handler))
    )


def test_selector_is_the_keccak_prefix() -> None:
    assert function_selector("getBatch(bytes32)") == keccak(text="getBatch(bytes32)")[:4]
    assert len(function_selector("getBatch(bytes32)")) == 4


def test_encode_call_prefixes_the_selector() -> None:
    data = encode_call("getBatch(bytes32)", ["bytes32"], [bytes.fromhex(BATCH_ID[2:])])
    assert data.startswith("0x" + function_selector("getBatch(bytes32)").hex())


def test_get_batch_decodes_the_result() -> None:
    result = "0x" + abi_encode(["bool", "uint32", "bool"], [True, 2, False]).hex()

    def handler(request: httpx.Request) -> httpx.Response:
        payload = json.loads(request.content)
        assert payload["method"] == "eth_call"
        assert payload["params"][0]["to"] == CONTRACT
        assert payload["params"][0]["data"].startswith("0x")
        return httpx.Response(200, json={"jsonrpc": "2.0", "id": 1, "result": result})

    assert reader_with(handler).get_batch(BATCH_ID) == BatchState(True, 2, False)


def test_get_station_decodes_the_result() -> None:
    station_id = bytes.fromhex("ab" * 32)
    result = "0x" + abi_encode(
        ["bool", "bytes32", "uint64", "uint32"], [True, station_id, 1831536000, 15]
    ).hex()
    handler = lambda request: httpx.Response(200, json={"result": result})  # noqa: E731

    station = reader_with(handler).get_station(STATION)
    assert station.active is True
    assert station.station_id == "0x" + "ab" * 32
    assert station.expires_at == 1831536000
    assert station.stage_mask == 15


def test_rpc_error_becomes_chain_error() -> None:
    handler = lambda request: httpx.Response(  # noqa: E731
        200, json={"error": {"code": -32000, "message": "execution reverted"}}
    )
    with pytest.raises(ChainError):
        reader_with(handler).get_batch(BATCH_ID)


def test_http_error_becomes_chain_error() -> None:
    with pytest.raises(ChainError):
        reader_with(lambda request: httpx.Response(500)).get_batch(BATCH_ID)


def test_missing_result_becomes_chain_error() -> None:
    handler = lambda request: httpx.Response(200, json={"jsonrpc": "2.0", "id": 1})  # noqa: E731
    with pytest.raises(ChainError):
        reader_with(handler).get_batch(BATCH_ID)


def test_transport_error_becomes_chain_error() -> None:
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("brak połączenia")

    with pytest.raises(ChainError):
        reader_with(handler).get_batch(BATCH_ID)


def test_bad_batch_id_is_rejected() -> None:
    with pytest.raises(ValueError):
        reader_with(lambda request: httpx.Response(200, json={})).get_batch("0x12")


@pytest.mark.parametrize("url", ["127.0.0.1:8545", "http://", "ws://127.0.0.1"])
def test_bad_rpc_url_is_rejected(url: str) -> None:
    with pytest.raises(ValueError):
        ChainReader(url, CONTRACT)


def test_bad_contract_address_is_rejected() -> None:
    with pytest.raises(ValueError):
        ChainReader(RPC_URL, "0x1234")
