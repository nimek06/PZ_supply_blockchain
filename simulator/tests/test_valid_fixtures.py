"""Testy poprawnych fixture'ów `report-valid-stageN.json` z `shared/fixtures/`.

Raport każdego etapu jest zgodny ze schematem i z regułą `qaPassed`, więc backend ma go
przyjąć (`202`). Etapy 0-3 są w jednej partii, żeby dało się je wysłać po kolei.
"""

from __future__ import annotations

import json
from pathlib import Path

import pytest
from supply_chain_simulator.canonical import canonical_text, report_hash
from supply_chain_simulator.certificate import certificate_id
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
    VALID_FIXTURE_BATCH_NUMBER,
    VALID_FIXTURE_STAGES,
    VECTOR_CONTRACT,
    VECTOR_DEADLINE,
    batch_id,
    negative_fixture_names,
    valid_fixture_name,
    valid_fixture_text,
)

FIXTURES = Path(__file__).resolve().parents[2] / "shared" / "fixtures"
TELEMETRY_SCHEMA_PATH = (
    Path(__file__).resolve().parents[2] / "shared" / "schemas" / "telemetry.schema.json"
)
NAMES = [valid_fixture_name(stage_id) for stage_id in VALID_FIXTURE_STAGES]


def load(stage_id: int) -> dict:
    return json.loads((FIXTURES / valid_fixture_name(stage_id)).read_text(encoding="utf-8"))


def rule_holds(stage_id: int, measurements: dict) -> bool:
    """Reguła `qaPassed=true` etapu (docs/product-scenario.md, DEC-02)."""
    if stage_id == 0:
        return measurements["checklistItemsPassed"] == measurements["checklistItemsTotal"]
    if stage_id == 1:
        return measurements["operationsCompleted"] == measurements["operationsTotal"]
    if stage_id == 2:
        return (
            measurements["defectsFound"] == 0
            and measurements["checklistItemsPassed"] == measurements["checklistItemsTotal"]
        )
    if stage_id == 3:
        return measurements["labelsApplied"] == measurements["unitsPacked"]
    raise AssertionError(f"nieznany etap: {stage_id}")


@pytest.fixture(scope="module")
def schema_validator():
    jsonschema = pytest.importorskip("jsonschema")
    schema = json.loads(TELEMETRY_SCHEMA_PATH.read_text(encoding="utf-8"))
    return jsonschema.Draft202012Validator(schema)


@pytest.mark.parametrize("stage_id", VALID_FIXTURE_STAGES)
def test_fixture_is_generated_reproducibly(stage_id: int) -> None:
    path = FIXTURES / valid_fixture_name(stage_id)
    assert path.is_file(), f"brak {path.name}, odpal --export-valid-fixtures"
    assert path.read_text(encoding="utf-8") == valid_fixture_text(stage_id)


@pytest.mark.parametrize("stage_id", VALID_FIXTURE_STAGES)
def test_report_is_schema_valid_and_keeps_the_rule(stage_id: int, schema_validator) -> None:
    report = load(stage_id)["report"]
    schema_validator.validate(report)
    assert report["stageId"] == stage_id
    assert report["qaPassed"] is True
    assert rule_holds(stage_id, report["measurements"])


@pytest.mark.parametrize("stage_id", VALID_FIXTURE_STAGES)
def test_hash_and_station_signature(stage_id: int) -> None:
    fixture = load(stage_id)
    assert fixture["canonicalReportJcs"] == canonical_text(fixture["report"])
    assert fixture["reportHash"] == report_hash(Report(**fixture["report"]))

    domain = Eip712Domain(**fixture["domain"])
    entry = fixture["stageCompletion"]
    message = StageCompletionMessage(**entry["message"])
    assert entry["message"]["reportHash"] == fixture["reportHash"]
    assert digest("StageCompletion", domain, message) == entry["digest"]
    signer = recover_signer("StageCompletion", domain, message, entry["signature"])
    assert signer.lower() == ANVIL_ADDRESS_1.lower()


@pytest.mark.parametrize("stage_id", VALID_FIXTURE_STAGES)
def test_submission_block_is_a_ready_request(stage_id: int) -> None:
    fixture = load(stage_id)
    submission = fixture["submission"]
    assert set(submission) == {"stationCertificateId", "reportHash", "deadline", "signature"}
    assert submission["stationCertificateId"] == certificate_id("CERT-2026-0001")
    assert submission["reportHash"] == fixture["reportHash"]
    assert submission["deadline"] == VECTOR_DEADLINE
    assert submission["deadline"] > fixture["report"]["timestamp"]
    assert submission["signature"] == fixture["stageCompletion"]["signature"]
    assert fixture["expectedOutcome"] == {
        "status": 202,
        "outcome": "ACCEPTED",
        "event": "StageCompleted",
    }


def test_only_quality_control_has_operator_approval() -> None:
    for stage_id in VALID_FIXTURE_STAGES:
        assert ("stageApproval" in load(stage_id)) == (stage_id == 2)

    fixture = load(2)
    domain = Eip712Domain(**fixture["domain"])
    entry = fixture["stageApproval"]
    message = StageApprovalMessage(**entry["message"])
    assert entry["message"]["reportHash"] == fixture["reportHash"]
    assert digest("StageApproval", domain, message) == entry["digest"]
    signer = recover_signer("StageApproval", domain, message, entry["signature"])
    assert signer.lower() == ANVIL_ADDRESS_2.lower()
    assert signer.lower() != fixture["report"]["stationAddress"].lower()


def test_all_stages_share_one_batch_in_order() -> None:
    fixtures = [load(stage_id) for stage_id in VALID_FIXTURE_STAGES]
    assert {f["batchNumber"] for f in fixtures} == {VALID_FIXTURE_BATCH_NUMBER}
    assert {f["report"]["batchId"] for f in fixtures} == {batch_id(VALID_FIXTURE_BATCH_NUMBER)}
    timestamps = [f["report"]["timestamp"] for f in fixtures]
    assert timestamps == sorted(timestamps)
    for f in fixtures:
        assert f["domain"]["verifyingContract"].lower() == VECTOR_CONTRACT.lower()


def test_batch_differs_from_negative_fixtures_and_shared_vector() -> None:
    used = {json.loads((FIXTURES / n).read_text(encoding="utf-8"))["batchNumber"]
            for n in [*negative_fixture_names(), "signature-vector.json"]}
    assert VALID_FIXTURE_BATCH_NUMBER not in used


def test_fixtures_contain_no_private_keys() -> None:
    keys = [format(ANVIL_KEY_1, "064x"), format(ANVIL_KEY_2, "064x")]
    for name in NAMES:
        text = (FIXTURES / name).read_text(encoding="utf-8").lower()
        assert not any(key in text for key in keys)
