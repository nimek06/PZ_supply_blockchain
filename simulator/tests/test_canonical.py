"""Testy kanonizacji JCS i Keccak-256 (T008)."""

from __future__ import annotations

import pytest
from supply_chain_simulator.canonical import (
    canonical_bytes,
    canonical_text,
    keccak256_hex,
    report_hash,
)
from supply_chain_simulator.models import Report
from supply_chain_simulator.vector import VECTOR_BATCH_NUMBER, batch_id, vector_report

# Keccak-256 pustego wejścia - wartość rozpoznawcza (NIST SHA3-256 daje inną).
EMPTY_KECCAK = "0xc5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470"


def test_keys_are_sorted_and_compact() -> None:
    assert canonical_text({"b": 1, "a": 2}) == '{"a":2,"b":1}'


def test_key_order_does_not_change_bytes() -> None:
    first = {"b": 1, "a": {"d": 4, "c": 3}}
    second = {"a": {"c": 3, "d": 4}, "b": 1}
    assert canonical_bytes(first) == canonical_bytes(second)
    assert keccak256_hex(canonical_bytes(first)) == keccak256_hex(canonical_bytes(second))


def test_utf8_is_not_escaped() -> None:
    payload = {"unit": "°C", "opis": "kontrola"}
    assert canonical_bytes(payload) == '{"opis":"kontrola","unit":"°C"}'.encode()


def test_booleans_and_nulls_are_minimal() -> None:
    assert canonical_text({"a": None, "b": True, "c": False}) == '{"a":null,"b":true,"c":false}'


def test_keccak256_is_not_sha3() -> None:
    assert keccak256_hex(b"") == EMPTY_KECCAK


def test_canonical_bytes_rejects_non_mapping() -> None:
    with pytest.raises(TypeError):
        canonical_bytes([1, 2, 3])  # type: ignore[arg-type]


def test_report_hash_is_stable_and_key_order_independent() -> None:
    report = vector_report()
    reordered = Report(**{k: v for k, v in reversed(list(report.model_dump().items()))})
    assert report_hash(report) == report_hash(reordered)


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("stageId", 1),
        ("timestamp", 1_800_000_001),
        ("qaPassed", False),
        ("measurements", {"temperatureMilliC": 21_501}),
    ],
)
def test_every_report_field_changes_the_hash(field: str, value: object) -> None:
    report = vector_report()
    payload = report.model_dump()
    payload[field] = value
    assert report_hash(Report(**payload)) != report_hash(report)


def test_address_case_does_not_change_the_hash() -> None:
    report = vector_report()
    upper = Report(
        **{**report.model_dump(), "stationAddress": "0x" + report.stationAddress[2:].upper()}
    )
    assert report_hash(upper) == report_hash(report)


def test_batch_id_is_keccak_of_batch_number() -> None:
    assert batch_id(VECTOR_BATCH_NUMBER) == vector_report().batchId
