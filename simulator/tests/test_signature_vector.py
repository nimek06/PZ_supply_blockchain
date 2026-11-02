"""Testy wspólnego wektora `shared/fixtures/signature-vector.json` (T008).

Ten sam plik czyta `contracts/test/SignatureVector.t.sol` (`test_SharedVector`).
Testy sprawdzają, że plik da się odtworzyć z modeli, że hash i digesty są
poprawne oraz że w pliku nie ma kluczy prywatnych.
"""

from __future__ import annotations

import json
from pathlib import Path

import pytest
from supply_chain_simulator.canonical import canonical_text, report_hash
from supply_chain_simulator.models import (
    Eip712Domain,
    Report,
    StageApprovalMessage,
    StageCompletionMessage,
)
from supply_chain_simulator.signing import digest, recover_signer
from supply_chain_simulator.vector import (
    ANVIL_ADDRESS_1,
    ANVIL_ADDRESS_2,
    ANVIL_KEY_1,
    ANVIL_KEY_2,
    VECTOR_BATCH_NUMBER,
    VECTOR_CHAIN_ID,
    VECTOR_CONTRACT,
    batch_id,
    signature_vector_text,
)

VECTOR_PATH = Path(__file__).resolve().parents[2] / "shared" / "fixtures" / "signature-vector.json"
EXPECTED_BATCH_ID = "0x79709e7401c7c94e8c6439dd2cd3729509c3c33d6b80b2df940e9c435df582f1"


@pytest.fixture(scope="module")
def vector() -> dict:
    assert VECTOR_PATH.is_file(), f"brak wspólnego wektora: {VECTOR_PATH}"
    return json.loads(VECTOR_PATH.read_text(encoding="utf-8"))


def test_vector_is_generated_reproducibly() -> None:
    assert VECTOR_PATH.read_text(encoding="utf-8") == signature_vector_text()


def test_domain_matches_the_agreed_values(vector: dict) -> None:
    assert vector["domain"]["name"] == "SupplyChain"
    assert vector["domain"]["version"] == "1"
    assert vector["domain"]["chainId"] == VECTOR_CHAIN_ID
    assert vector["domain"]["verifyingContract"].lower() == VECTOR_CONTRACT.lower()


def test_report_is_a_valid_report_v1(vector: dict) -> None:
    report = Report(**vector["report"])
    assert report.schemaVersion == 1
    assert report.batchId == batch_id(VECTOR_BATCH_NUMBER) == EXPECTED_BATCH_ID
    assert report.stationAddress == ANVIL_ADDRESS_1.lower()
    assert report.qaPassed is True
    assert "nonce" not in vector["report"]


def test_canonical_bytes_and_hash_match_the_report(vector: dict) -> None:
    report = Report(**vector["report"])
    assert vector["canonicalReportJcs"] == canonical_text(vector["report"])
    assert vector["reportHash"] == report_hash(report)


@pytest.mark.parametrize(
    ("block", "message_type", "signer"),
    [
        ("stageCompletion", StageCompletionMessage, ANVIL_ADDRESS_1),
        ("stageApproval", StageApprovalMessage, ANVIL_ADDRESS_2),
    ],
)
def test_signature_block_is_correct(
    vector: dict, block: str, message_type: type, signer: str
) -> None:
    entry = vector[block]
    domain = Eip712Domain(**vector["domain"])
    message = message_type(**entry["message"])
    primary_type = "StageCompletion" if block == "stageCompletion" else "StageApproval"

    assert digest(primary_type, domain, message) == entry["digest"]
    assert recover_signer(primary_type, domain, message, entry["signature"]).lower() == (
        signer.lower()
    )
    assert entry["signer"].lower() == signer.lower()
    assert entry["message"]["batchId"] == vector["report"]["batchId"]
    assert entry["message"]["stageId"] == vector["report"]["stageId"]
    assert entry["message"]["reportHash"] == vector["reportHash"]


def test_station_and_operator_blocks_use_different_accounts(vector: dict) -> None:
    assert vector["stageCompletion"]["signer"].lower() != vector["stageApproval"]["signer"].lower()
    assert vector["stageCompletion"]["signature"] != vector["stageApproval"]["signature"]


def test_fixture_contains_no_private_keys() -> None:
    text = VECTOR_PATH.read_text(encoding="utf-8").lower()
    for key in (ANVIL_KEY_1, ANVIL_KEY_2):
        assert format(key, "064x") not in text


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("batchId", "0x" + "11" * 32),
        ("stageId", 1),
        ("reportHash", "0x" + "22" * 32),
        ("deadline", 1_800_000_601),
    ],
)
def test_station_mutation_changes_the_digest(vector: dict, field: str, value: object) -> None:
    domain = Eip712Domain(**vector["domain"])
    payload = dict(vector["stageCompletion"]["message"])
    payload[field] = value
    assert digest("StageCompletion", domain, StageCompletionMessage(**payload)) != (
        vector["stageCompletion"]["digest"]
    )


def test_operator_mutation_changes_the_digest(vector: dict) -> None:
    domain = Eip712Domain(**vector["domain"])
    payload = dict(vector["stageApproval"]["message"])
    payload["reportHash"] = "0x" + "33" * 32
    assert digest("StageApproval", domain, StageApprovalMessage(**payload)) != (
        vector["stageApproval"]["digest"]
    )


def test_signatures_are_not_interchangeable(vector: dict) -> None:
    domain = Eip712Domain(**vector["domain"])
    message = StageCompletionMessage(**vector["stageCompletion"]["message"])
    recovered = recover_signer(
        "StageCompletion", domain, message, vector["stageApproval"]["signature"]
    )
    assert recovered.lower() != ANVIL_ADDRESS_1.lower()
