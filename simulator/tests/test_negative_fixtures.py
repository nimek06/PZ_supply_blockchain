"""Testy negatywnych fixture'ów z `shared/fixtures/` (tydzień 2).

Dwie grupy (`simulator/src/supply_chain_simulator/vector.py`, sekcja „Wektory negatywne"):

- `report-profile-violation-stageN.json` (N=0..3) - raport etapu z `qaPassed=true`
  łamiącym regułę `qaPassed` swojego etapu; format JSON Schema i podpis stacji są
  poprawne, więc backend ma odrzucić raport kodem `PROFILE_VIOLATION` i statusem `422`
  (`docs/corrections.md`), a nie `403`;
- `report-qa-fail.json` - etap 1 z `qaPassed=false` ZGODNYM z regułą (poprawny raport
  z wynikiem negatywnym) -> `202` z `outcome=REJECTED`, nie `422`. Kontrola, że
  walidator profilu nie odrzuca zgodnego negatywu.
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
    PROFILE_VIOLATION_PROFILES,
    PROFILE_VIOLATION_REASONS,
    PROFILE_VIOLATION_STAGES,
    QA_FAIL_FIXTURE_NAME,
    VECTOR_CONTRACT,
    VECTOR_DEADLINE,
    batch_id,
    negative_fixture_names,
    profile_violation_fixture_name,
    profile_violation_fixture_text,
    qa_fail_fixture_text,
)

FIXTURES = Path(__file__).resolve().parents[2] / "shared" / "fixtures"
SHARED_VECTOR = FIXTURES / "signature-vector.json"
TELEMETRY_SCHEMA_PATH = (
    Path(__file__).resolve().parents[2] / "shared" / "schemas" / "telemetry.schema.json"
)
VIOLATION_NAMES = [
    profile_violation_fixture_name(stage_id) for stage_id in PROFILE_VIOLATION_STAGES
]
ALL_NAMES = negative_fixture_names()


def load(name: str) -> dict:
    return json.loads((FIXTURES / name).read_text(encoding="utf-8"))


def broken_rule(stage_id: int, measurements: dict) -> bool:
    """Zwraca True, gdy pomiary łamią regułę `qaPassed` etapu `stage_id`."""
    if stage_id == 0:
        return measurements["checklistItemsPassed"] != measurements["checklistItemsTotal"]
    if stage_id == 1:
        return measurements["operationsCompleted"] != measurements["operationsTotal"]
    if stage_id == 2:
        return (
            measurements["defectsFound"] != 0
            or measurements["checklistItemsPassed"] != measurements["checklistItemsTotal"]
        )
    if stage_id == 3:
        return measurements["labelsApplied"] != measurements["unitsPacked"]
    raise AssertionError(f"nieznany etap: {stage_id}")


@pytest.fixture(scope="module")
def schema_validator():
    jsonschema = pytest.importorskip("jsonschema")
    schema = json.loads(TELEMETRY_SCHEMA_PATH.read_text(encoding="utf-8"))
    return jsonschema.Draft202012Validator(schema)


def test_all_negative_fixtures_exist() -> None:
    for name in ALL_NAMES:
        assert (FIXTURES / name).is_file(), f"brak negatywnego fixture'u: {name}"


def test_negative_fixture_names_are_unique_and_distinct_from_shared_vector() -> None:
    assert len(set(ALL_NAMES)) == len(ALL_NAMES)
    assert "signature-vector.json" not in ALL_NAMES


@pytest.mark.parametrize("name", VIOLATION_NAMES)
def test_violation_fixture_is_generated_reproducibly(name: str) -> None:
    stage_id = int(name.removesuffix(".json").removeprefix("report-profile-violation-stage"))
    text = (FIXTURES / name).read_text(encoding="utf-8")
    assert text == profile_violation_fixture_text(stage_id)


def test_qa_fail_fixture_is_generated_reproducibly() -> None:
    assert (FIXTURES / QA_FAIL_FIXTURE_NAME).read_text(encoding="utf-8") == qa_fail_fixture_text()


@pytest.mark.parametrize("stage_id", PROFILE_VIOLATION_STAGES)
def test_violation_is_schema_valid_but_breaks_the_rule(stage_id: int, schema_validator) -> None:
    report = load(profile_violation_fixture_name(stage_id))["report"]
    schema_validator.validate(report)  # format OK
    assert report["stageId"] == stage_id
    assert report["qaPassed"] is True
    assert broken_rule(stage_id, report["measurements"])


def test_qa_fail_is_schema_valid_and_consistent(schema_validator) -> None:
    fixture = load(QA_FAIL_FIXTURE_NAME)
    report = fixture["report"]
    schema_validator.validate(report)
    assert report["stageId"] == 1
    assert report["qaPassed"] is False
    measurements = report["measurements"]
    assert measurements["operationsCompleted"] < measurements["operationsTotal"]
    assert fixture["expectedOutcome"] == {
        "status": 202,
        "outcome": "REJECTED",
        "event": "StageRejected",
    }


@pytest.mark.parametrize("stage_id", PROFILE_VIOLATION_STAGES)
def test_expected_violation_matches_backend_behaviour(stage_id: int) -> None:
    expected = load(profile_violation_fixture_name(stage_id))["expectedViolation"]
    assert expected["code"] == "PROFILE_VIOLATION"
    assert expected["status"] == 422
    assert expected["profile"] == PROFILE_VIOLATION_PROFILES[stage_id]
    assert expected["reason"] == PROFILE_VIOLATION_REASONS[stage_id]


@pytest.mark.parametrize("name", ALL_NAMES)
def test_canonical_bytes_and_hash_match_the_report(name: str) -> None:
    fixture = load(name)
    report = Report(**fixture["report"])
    assert fixture["canonicalReportJcs"] == canonical_text(fixture["report"])
    assert fixture["reportHash"] == report_hash(report)


@pytest.mark.parametrize("name", ALL_NAMES)
def test_signature_is_valid_and_signed_by_the_station(name: str) -> None:
    fixture = load(name)
    domain = Eip712Domain(**fixture["domain"])
    entry = fixture["stageCompletion"]
    message = StageCompletionMessage(**entry["message"])

    assert entry["message"]["reportHash"] == fixture["reportHash"]
    assert entry["message"]["stageId"] == fixture["report"]["stageId"]
    assert entry["message"]["qaPassed"] == fixture["report"]["qaPassed"]
    assert digest("StageCompletion", domain, message) == entry["digest"]
    assert recover_signer("StageCompletion", domain, message, entry["signature"]).lower() == (
        ANVIL_ADDRESS_1.lower()
    )
    assert entry["signer"].lower() == ANVIL_ADDRESS_1.lower()


@pytest.mark.parametrize("name", ALL_NAMES)
def test_submission_block_matches_the_backend_request(name: str) -> None:
    fixture = load(name)
    submission = fixture["submission"]
    assert set(submission) == {"stationCertificateId", "reportHash", "deadline", "signature"}
    assert submission["stationCertificateId"] == certificate_id("CERT-2026-0001")
    assert submission["stationCertificateId"] == keccak256_text("CERT-2026-0001")
    assert submission["reportHash"] == fixture["reportHash"]
    assert submission["deadline"] == VECTOR_DEADLINE
    assert submission["signature"] == fixture["stageCompletion"]["signature"]


@pytest.mark.parametrize("name", ALL_NAMES)
def test_fixture_uses_the_shared_domain(name: str) -> None:
    domain = load(name)["domain"]
    assert domain["name"] == "SupplyChain"
    assert domain["version"] == "1"
    assert domain["chainId"] == 31337
    assert domain["verifyingContract"].lower() == VECTOR_CONTRACT.lower()


def test_batch_numbers_are_distinct_and_match_keccak() -> None:
    batch_ids = []
    for name in ALL_NAMES:
        fixture = load(name)
        assert fixture["report"]["batchId"] == batch_id(fixture["batchNumber"])
        batch_ids.append(fixture["report"]["batchId"])
    assert len(set(batch_ids)) == len(batch_ids)


def test_fixtures_contrast_with_the_valid_shared_vector() -> None:
    """Wspólny wektor etapu 2 jest zgodny z profilem (defectsFound == 0)."""
    shared = json.loads(SHARED_VECTOR.read_text(encoding="utf-8"))
    assert shared["report"]["measurements"]["defectsFound"] == 0
    assert shared["report"]["qaPassed"] is True


def test_fixtures_contain_no_private_keys() -> None:
    private_key = format(ANVIL_KEY_1, "064x")
    for name in ALL_NAMES:
        assert private_key not in (FIXTURES / name).read_text(encoding="utf-8").lower()
