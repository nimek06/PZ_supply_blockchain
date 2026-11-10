"""Testy modelu i ładowania scenariuszy YAML (T020)."""

from __future__ import annotations

from pathlib import Path

import pytest
import yaml
from supply_chain_simulator.canonical import keccak256_text
from supply_chain_simulator.scenario import (
    Scenario,
    ScenarioError,
    default_scenarios_directory,
    load_scenario,
)


def happy_path() -> Scenario:
    return load_scenario(default_scenarios_directory() / "ebike-happy-path.yaml")


def base_scenario() -> dict:
    return {
        "scenarioVersion": 1,
        "name": "test",
        "batchNumber": "B-2026-0001",
        "station": {"reference": "ST-001"},
        "seed": 1,
        "steps": [{"stageId": 0, "expect": {"outcome": "ACCEPTED"}}],
    }


def write_scenario(tmp_path: Path, data: object) -> Path:
    path = tmp_path / "scenario.yaml"
    path.write_text(yaml.safe_dump(data), encoding="utf-8")
    return path


def test_default_directory_contains_both_scenarios() -> None:
    directory = default_scenarios_directory()
    assert (directory / "ebike-happy-path.yaml").is_file()
    assert (directory / "ebike-qa-fail.yaml").is_file()


def test_happy_path_fields() -> None:
    scenario = happy_path()
    assert scenario.scenarioVersion == 1
    assert scenario.name == "ebike-happy-path"
    assert scenario.batchNumber == "B-2026-0001"
    assert scenario.station.reference == "ST-001"
    assert scenario.seed == 20261105
    assert [step.stageId for step in scenario.steps] == [0, 1]
    assert all(step.expect.outcome is None for step in scenario.steps)
    assert all(step.forceQaFail is False for step in scenario.steps)
    assert all(step.expect.httpStatus == 202 for step in scenario.steps)


def test_qa_fail_scenario_marks_the_failing_step() -> None:
    scenario = load_scenario(default_scenarios_directory() / "ebike-qa-fail.yaml")
    assert [step.stageId for step in scenario.steps] == [0, 1]
    assert scenario.steps[0].forceQaFail is False
    assert scenario.steps[1].forceQaFail is True
    assert scenario.steps[1].expect.outcome is None


def test_batch_id_is_keccak_of_batch_number() -> None:
    assert happy_path().batch_id() == keccak256_text("B-2026-0001")


def test_http_status_defaults_to_202(tmp_path: Path) -> None:
    scenario = load_scenario(write_scenario(tmp_path, base_scenario()))
    assert scenario.steps[0].expect.httpStatus == 202


def test_outcome_is_optional_but_accepted(tmp_path: Path) -> None:
    data = base_scenario()
    scenario = load_scenario(write_scenario(tmp_path, data))
    assert scenario.steps[0].expect.outcome == "ACCEPTED"


def test_missing_file_raises(tmp_path: Path) -> None:
    with pytest.raises(ScenarioError):
        load_scenario(tmp_path / "brak.yaml")


def test_non_mapping_yaml_raises(tmp_path: Path) -> None:
    with pytest.raises(ScenarioError):
        load_scenario(write_scenario(tmp_path, [1, 2, 3]))


def test_invalid_yaml_raises(tmp_path: Path) -> None:
    path = tmp_path / "broken.yaml"
    path.write_text("name: [unclosed", encoding="utf-8")
    with pytest.raises(ScenarioError):
        load_scenario(path)


def test_unknown_top_level_field_raises(tmp_path: Path) -> None:
    data = base_scenario()
    data["extra"] = 1
    with pytest.raises(ScenarioError):
        load_scenario(write_scenario(tmp_path, data))


def test_unknown_step_field_raises(tmp_path: Path) -> None:
    data = base_scenario()
    data["steps"][0]["extra"] = 1
    with pytest.raises(ScenarioError):
        load_scenario(write_scenario(tmp_path, data))


@pytest.mark.parametrize("batch_number", ["2026-0001", "B-26-0001", "B-2026-1"])
def test_bad_batch_number_raises(tmp_path: Path, batch_number: str) -> None:
    data = base_scenario()
    data["batchNumber"] = batch_number
    with pytest.raises(ScenarioError):
        load_scenario(write_scenario(tmp_path, data))


def test_stages_must_be_sequential(tmp_path: Path) -> None:
    data = base_scenario()
    data["steps"] = [{"stageId": 1, "expect": {"outcome": "ACCEPTED"}}]
    with pytest.raises(ScenarioError):
        load_scenario(write_scenario(tmp_path, data))


def test_empty_steps_are_rejected(tmp_path: Path) -> None:
    data = base_scenario()
    data["steps"] = []
    with pytest.raises(ScenarioError):
        load_scenario(write_scenario(tmp_path, data))


def test_unknown_outcome_is_rejected(tmp_path: Path) -> None:
    data = base_scenario()
    data["steps"][0]["expect"]["outcome"] = "MAYBE"
    with pytest.raises(ScenarioError):
        load_scenario(write_scenario(tmp_path, data))
