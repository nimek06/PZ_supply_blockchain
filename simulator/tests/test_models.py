"""Testy ścisłych modeli raportu v1 (T008)."""

from __future__ import annotations

import pytest
from pydantic import ValidationError
from supply_chain_simulator.models import (
    MAX_SAFE_INTEGER,
    Eip712Domain,
    Report,
    StageApprovalMessage,
    StageCompletionMessage,
)

BATCH_ID = "0x79709e7401c7c94e8c6439dd2cd3729509c3c33d6b80b2df940e9c435df582f1"
ADDRESS = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8"


def uppercase_address(address: str) -> str:
    """Zamienia na wielkie litery tylko część hex (prefiks `0x` zostaje)."""
    return "0x" + address[2:].upper()


def valid_report(**overrides: object) -> dict[str, object]:
    payload: dict[str, object] = {
        "schemaVersion": 1,
        "batchId": BATCH_ID,
        "stageId": 2,
        "stationAddress": ADDRESS,
        "timestamp": 1_800_000_000,
        "measurements": {"temperatureMilliC": 21_500},
        "qaPassed": True,
    }
    payload.update(overrides)
    return payload


def test_valid_report_is_accepted() -> None:
    report = Report(**valid_report())
    assert report.schemaVersion == 1
    assert report.stageId == 2
    assert report.qaPassed is True


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("unknownField", 1),
        ("nonce", 7),
        ("signature", "0x"),
        ("ipfsCid", "bafkrei"),
    ],
)
def test_unknown_fields_are_rejected(field: str, value: object) -> None:
    with pytest.raises(ValidationError):
        Report(**valid_report(**{field: value}))


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("schemaVersion", "1"),
        ("qaPassed", "true"),
        ("qaPassed", 1),
        ("stageId", "2"),
        ("stageId", 2.0),
        ("timestamp", 1_800_000_000.0),
        ("measurements", {"temperatureMilliC": 21.5}),
    ],
)
def test_implicit_conversions_are_rejected(field: str, value: object) -> None:
    with pytest.raises(ValidationError):
        Report(**valid_report(**{field: value}))


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("stageId", 4),
        ("stageId", -1),
        ("timestamp", -1),
        ("timestamp", MAX_SAFE_INTEGER + 1),
        ("batchId", BATCH_ID.upper().replace("0X", "0x")),
        ("batchId", "0x1234"),
        ("measurements", {}),
        ("measurements", {"temperature_milli_c": 21_500}),
        ("stationAddress", "0x1234"),
    ],
)
def test_invalid_values_are_rejected(field: str, value: object) -> None:
    with pytest.raises(ValidationError):
        Report(**valid_report(**{field: value}))


def test_uppercase_batch_id_is_rejected_but_address_is_normalized() -> None:
    with pytest.raises(ValidationError):
        Report(**valid_report(batchId=BATCH_ID.replace("b", "B", 1)))

    report = Report(**valid_report(stationAddress=uppercase_address(ADDRESS)))
    assert report.stationAddress == ADDRESS.lower()


def test_domain_address_is_normalized() -> None:
    domain = Eip712Domain(
        name="SupplyChain",
        version="1",
        chainId=31337,
        verifyingContract=ADDRESS,
    )
    assert domain.verifyingContract == ADDRESS.lower()


def test_domain_rejects_extra_fields() -> None:
    with pytest.raises(ValidationError):
        Eip712Domain(
            name="SupplyChain",
            version="1",
            chainId=31337,
            verifyingContract=ADDRESS,
            salt="0x00",
        )


def test_signed_structures_have_no_nonce() -> None:
    completion = StageCompletionMessage(
        batchId=BATCH_ID,
        stageId=2,
        reportHash=BATCH_ID,
        qaPassed=True,
        deadline=1_800_000_600,
    )
    assert "nonce" not in completion.model_dump()

    with pytest.raises(ValidationError):
        StageApprovalMessage(
            batchId=BATCH_ID,
            stageId=2,
            reportHash=BATCH_ID,
            deadline=1_800_000_600,
            nonce=1,
        )
