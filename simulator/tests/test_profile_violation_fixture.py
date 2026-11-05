"""Testy negatywnego wektora `shared/fixtures/report-profile-violation.json` (tydzień 2).

Wektor opisuje raport etapu 2 niezgodny z regułą `qaPassed` profilu
`profileQualityControlV1` (`qaPassed=true` przy `defectsFound > 0`). Format JSON
Schema jest poprawny, a podpis stacji jest ważny, więc backend ma odrzucić raport
kodem `PROFILE_VIOLATION` i statusem `422` (`docs/corrections.md`), a nie `403`.
Blok `submission` jest ciałem żądania `POST /api/v1/batches/{batchId}/stages`.
"""

from __future__ import annotations

import json
from pathlib import Path

import pytest
from supply_chain_simulator.canonical import canonical_text, keccak256_text, report_hash
from supply_chain_simulator.certificate import certificate_id
from supply_chain_simulator.models import Eip712Domain, Report, StageCompletionMessage
from supply_chain_simulator.signing import digest, recover_signer
from supply_chain_simulator.vector import (
    ANVIL_ADDRESS_1,
    ANVIL_KEY_1,
    PROFILE_VIOLATION_BATCH_NUMBER,
    VECTOR_CONTRACT,
    VECTOR_DEADLINE,
    batch_id,
    profile_violation_fixture_text,
)

FIXTURE_PATH = (
    Path(__file__).resolve().parents[2]
    / "shared"
    / "fixtures"
    / "report-profile-violation.json"
)
TELEMETRY_SCHEMA_PATH = (
    Path(__file__).resolve().parents[2] / "shared" / "schemas" / "telemetry.schema.json"
)
EXPECTED_BATCH_ID = "0xac97be7635b61242af787dc0dba75a7be126224c3c21724f0f077385af24e0d4"


@pytest.fixture(scope="module")
def fixture() -> dict:
    assert FIXTURE_PATH.is_file(), f"brak negatywnego wektora: {FIXTURE_PATH}"
    return json.loads(FIXTURE_PATH.read_text(encoding="utf-8"))


def test_fixture_is_generated_reproducibly() -> None:
    assert FIXTURE_PATH.read_text(encoding="utf-8") == profile_violation_fixture_text()


def test_report_has_the_expected_batch_and_stage(fixture: dict) -> None:
    report = Report(**fixture["report"])
    assert report.batchId == batch_id(PROFILE_VIOLATION_BATCH_NUMBER) == EXPECTED_BATCH_ID
    assert report.stageId == 2
    assert report.stationAddress == ANVIL_ADDRESS_1.lower()
    assert report.timestamp == 1_800_000_000


def test_report_is_json_schema_valid_but_violates_the_qa_passed_rule(fixture: dict) -> None:
    """Format przechodzi schema, ale reguła aplikacji jest złamana (to jest cała istota)."""
    jsonschema = pytest.importorskip("jsonschema")
    schema = json.loads(TELEMETRY_SCHEMA_PATH.read_text(encoding="utf-8"))
    validator = jsonschema.Draft202012Validator(schema)
    validator.validate(fixture["report"])  # format OK

    measurements = fixture["report"]["measurements"]
    assert fixture["report"]["qaPassed"] is True
    assert measurements["defectsFound"] > 0
    # Reguła etapu 2: qaPassed=true wymaga defectsFound == 0.
    assert measurements["checklistItemsPassed"] == measurements["checklistItemsTotal"]


def test_contrast_with_the_shared_vector() -> None:
    """Ten sam etap, ale wektor podpisu jest zgodny z profilem (defectsFound == 0)."""
    shared = json.loads(
        (
            Path(__file__).resolve().parents[2]
            / "shared"
            / "fixtures"
            / "signature-vector.json"
        ).read_text(encoding="utf-8")
    )
    assert shared["report"]["measurements"]["defectsFound"] == 0
    assert shared["report"]["measurements"]["inspectedUnits"] == 10


def test_canonical_bytes_and_hash_match_the_report(fixture: dict) -> None:
    report = Report(**fixture["report"])
    assert fixture["canonicalReportJcs"] == canonical_text(fixture["report"])
    assert fixture["reportHash"] == report_hash(report)


def test_signature_is_valid_and_signed_by_the_station(fixture: dict) -> None:
    domain = Eip712Domain(**fixture["domain"])
    entry = fixture["stageCompletion"]
    message = StageCompletionMessage(**entry["message"])

    assert entry["message"]["reportHash"] == fixture["reportHash"]
    assert entry["message"]["qaPassed"] is True
    assert entry["message"]["stageId"] == 2
    assert digest("StageCompletion", domain, message) == entry["digest"]
    assert recover_signer("StageCompletion", domain, message, entry["signature"]).lower() == (
        ANVIL_ADDRESS_1.lower()
    )
    assert entry["signer"].lower() == ANVIL_ADDRESS_1.lower()


def test_submission_block_matches_the_backend_request(fixture: dict) -> None:
    submission = fixture["submission"]
    assert set(submission) == {"stationCertificateId", "reportHash", "deadline", "signature"}
    assert submission["stationCertificateId"] == certificate_id("CERT-2026-0001")
    assert submission["stationCertificateId"] == keccak256_text("CERT-2026-0001")
    assert submission["reportHash"] == fixture["reportHash"]
    assert submission["deadline"] == VECTOR_DEADLINE
    assert submission["signature"] == fixture["stageCompletion"]["signature"]


def test_expected_violation_matches_documented_backend_behaviour(fixture: dict) -> None:
    expected = fixture["expectedViolation"]
    assert expected["code"] == "PROFILE_VIOLATION"
    assert expected["status"] == 422
    assert expected["profile"] == "profileQualityControlV1"


def test_fixture_uses_the_same_domain_as_the_shared_vector(fixture: dict) -> None:
    assert fixture["domain"]["name"] == "SupplyChain"
    assert fixture["domain"]["version"] == "1"
    assert fixture["domain"]["chainId"] == 31337
    assert fixture["domain"]["verifyingContract"].lower() == VECTOR_CONTRACT.lower()


def test_fixture_contains_no_private_keys() -> None:
    text = FIXTURE_PATH.read_text(encoding="utf-8").lower()
    assert format(ANVIL_KEY_1, "064x") not in text
