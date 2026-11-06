"""Testy deterministycznych generatorów pomiarów e-bike (T009).

Kryterium z planu: ten sam seed -> ten sam raport. Sprawdzamy też, że oba tryby
(zgodny i `force_qa_fail`) dają raport zgodny ze schematem i profilem, a `qaPassed`
liczy reguła etapu.
"""

from __future__ import annotations

import json
from pathlib import Path

import pytest
from supply_chain_simulator.profiles import load_profiles, validate_measurements
from supply_chain_simulator.telemetry import (
    MEASUREMENT_RECIPES,
    SUPPORTED_STAGES,
    generate_measurements,
    generate_report,
    qa_passed,
)

REPO_ROOT = Path(__file__).resolve().parents[2]
TELEMETRY_SCHEMA_PATH = REPO_ROOT / "shared" / "schemas" / "telemetry.schema.json"
BATCH_ID = "0x" + "ab" * 32
STATION = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8"
TIMESTAMP = 1_800_000_000


@pytest.fixture(scope="module")
def profiles():
    return load_profiles()


@pytest.fixture(scope="module")
def schema_validator():
    jsonschema = pytest.importorskip("jsonschema")
    schema = json.loads(TELEMETRY_SCHEMA_PATH.read_text(encoding="utf-8"))
    return jsonschema.Draft202012Validator(schema)


def report_for(stage_id: int, seed: int, *, force_qa_fail: bool = False):
    return generate_report(
        stage_id,
        batch_id=BATCH_ID,
        station_address=STATION,
        timestamp=TIMESTAMP,
        seed=seed,
        force_qa_fail=force_qa_fail,
    )


def test_only_the_four_known_stages_are_supported() -> None:
    assert SUPPORTED_STAGES == (0, 1, 2, 3)
    assert sorted(MEASUREMENT_RECIPES) == [0, 1, 2, 3]


@pytest.mark.parametrize("stage_id", SUPPORTED_STAGES)
def test_same_seed_gives_the_same_report(stage_id: int) -> None:
    first = report_for(stage_id, 7)
    second = report_for(stage_id, 7)
    assert first.model_dump(mode="json") == second.model_dump(mode="json")


def test_generation_order_does_not_matter() -> None:
    """Strumień losowy etapu jest niezależny od kolejności wywołań."""
    before = report_for(2, 11)
    report_for(1, 11)
    after = report_for(2, 11)
    assert before.model_dump(mode="json") == after.model_dump(mode="json")


@pytest.mark.parametrize("stage_id", SUPPORTED_STAGES)
def test_generated_report_is_schema_valid_and_passes(
    stage_id: int, profiles, schema_validator
) -> None:
    report = report_for(stage_id, 3)
    schema_validator.validate(report.model_dump(mode="json"))
    validate_measurements(profiles[stage_id], report.measurements)
    assert report.qaPassed is True
    assert qa_passed(stage_id, report.measurements) is True


@pytest.mark.parametrize("stage_id", SUPPORTED_STAGES)
def test_force_qa_fail_is_a_consistent_negative(
    stage_id: int, profiles, schema_validator
) -> None:
    """Wynik negatywny mieści się w profilu: to NIE naruszenie profilu (brak 422)."""
    report = report_for(stage_id, 5, force_qa_fail=True)
    schema_validator.validate(report.model_dump(mode="json"))
    validate_measurements(profiles[stage_id], report.measurements)
    assert report.qaPassed is False
    assert qa_passed(stage_id, report.measurements) is False


@pytest.mark.parametrize("stage_id", SUPPORTED_STAGES)
def test_report_keeps_the_caller_inputs(stage_id: int) -> None:
    report = report_for(stage_id, 1)
    assert report.batchId == BATCH_ID
    assert report.stageId == stage_id
    assert report.timestamp == TIMESTAMP
    assert report.stationAddress == STATION.lower()


def test_different_seeds_vary_the_cycle_time() -> None:
    values = {generate_measurements(1, seed)["cycleTimeMs"] for seed in range(20)}
    assert len(values) > 1


@pytest.mark.parametrize("stage_id", SUPPORTED_STAGES)
def test_measurements_are_plain_integers(stage_id: int) -> None:
    measurements = generate_measurements(stage_id, 0)
    for value in measurements.values():
        assert isinstance(value, int) and not isinstance(value, bool)


def test_passing_an_explicit_profile_matches_the_default(profiles) -> None:
    explicit = generate_measurements(2, 4, profile=profiles[2])
    assert explicit == generate_measurements(2, 4)


def test_unknown_stage_is_rejected() -> None:
    with pytest.raises(ValueError):
        generate_measurements(9, 0)
    with pytest.raises(ValueError):
        report_for(9, 0)


@pytest.mark.parametrize("seed", [-1, True, 1.5, "7"])
def test_invalid_seed_is_rejected(seed: object) -> None:
    with pytest.raises(ValueError):
        generate_measurements(0, seed)  # type: ignore[arg-type]


def test_generated_values_stay_within_profile_bounds(profiles) -> None:
    for stage_id in SUPPORTED_STAGES:
        profile = profiles[stage_id]
        for seed in range(10):
            validate_measurements(profile, generate_measurements(stage_id, seed))
