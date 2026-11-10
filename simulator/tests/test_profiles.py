"""Testy profili pomiarów czytanych ze schematu (T009)."""

from __future__ import annotations

import json
from pathlib import Path

import pytest
from supply_chain_simulator.profiles import (
    default_profile_schema_path,
    load_profiles,
    profiles_from_schema,
    validate_measurements,
)

REPO_ROOT = Path(__file__).resolve().parents[2]


@pytest.fixture(scope="module")
def profiles():
    return load_profiles()


def test_default_schema_path_points_to_shared_schema() -> None:
    path = default_profile_schema_path()
    assert path.is_file()
    assert path == REPO_ROOT / "shared" / "schemas" / "telemetry.schema.json"


def test_all_four_stages_are_loaded(profiles) -> None:
    assert sorted(profiles) == [0, 1, 2, 3]


def test_profile_names_match_schema_definitions(profiles) -> None:
    assert profiles[0].name == "profilePreparationV1"
    assert profiles[1].name == "profileAssemblyV1"
    assert profiles[2].name == "profileQualityControlV1"
    assert profiles[3].name == "profilePackagingV1"


def test_required_keys_equal_defined_fields(profiles) -> None:
    for stage_id, profile in profiles.items():
        assert set(profile.required) == set(profile.fields), stage_id


def test_stage_two_matches_the_schema_ranges(profiles) -> None:
    profile = profiles[2]
    assert set(profile.required) == {
        "inspectedUnits",
        "defectsFound",
        "checklistItemsTotal",
        "checklistItemsPassed",
    }
    assert (profile.fields["inspectedUnits"].minimum, profile.fields["inspectedUnits"].maximum) == (
        1,
        10000,
    )
    assert (profile.fields["checklistItemsTotal"].minimum,
            profile.fields["checklistItemsTotal"].maximum) == (1, 100)
    assert (profile.fields["checklistItemsPassed"].minimum,
            profile.fields["checklistItemsPassed"].maximum) == (0, 100)


def test_count_reference_is_resolved(profiles) -> None:
    """`defectsFound`/`labelsApplied` używają `$ref: count` (0..10000)."""
    defects = profiles[2].fields["defectsFound"]
    labels = profiles[3].fields["labelsApplied"]
    assert (defects.minimum, defects.maximum) == (0, 10000)
    assert (labels.minimum, labels.maximum) == (0, 10000)


def test_valid_measurements_pass(profiles) -> None:
    validate_measurements(
        profiles[1],
        {"operationsTotal": 8, "operationsCompleted": 8, "cycleTimeMs": 420_000},
    )


def test_missing_and_unknown_keys_are_rejected(profiles) -> None:
    with pytest.raises(ValueError):
        validate_measurements(profiles[0], {"checklistItemsTotal": 12})
    with pytest.raises(ValueError):
        validate_measurements(
            profiles[0],
            {"checklistItemsTotal": 12, "checklistItemsPassed": 12, "extra": 1},
        )


@pytest.mark.parametrize("value", [12.0, "12", True])
def test_non_integer_values_are_rejected(profiles, value: object) -> None:
    with pytest.raises(ValueError):
        validate_measurements(
            profiles[0], {"checklistItemsTotal": value, "checklistItemsPassed": 12}
        )


def test_out_of_range_values_are_rejected(profiles) -> None:
    profile = profiles[2]
    with pytest.raises(ValueError):
        validate_measurements(
            profile,
            {
                "inspectedUnits": 10,
                "defectsFound": 10_001,
                "checklistItemsTotal": 12,
                "checklistItemsPassed": 12,
            },
        )
    with pytest.raises(ValueError):
        validate_measurements(
            profile,
            {
                "inspectedUnits": 10,
                "defectsFound": 0,
                "checklistItemsTotal": 12,
                "checklistItemsPassed": 101,
            },
        )


def test_missing_schema_file_raises(tmp_path: Path) -> None:
    with pytest.raises(FileNotFoundError):
        load_profiles(tmp_path / "brak.json")


def test_schema_without_profiles_is_rejected() -> None:
    with pytest.raises(ValueError):
        profiles_from_schema({"$defs": {}, "allOf": []})


def test_profile_without_additional_properties_false_is_rejected() -> None:
    schema = {
        "$defs": {
            "profileX": {
                "type": "object",
                "required": ["a"],
                "properties": {"a": {"type": "integer", "minimum": 0, "maximum": 1}},
            }
        },
        "allOf": [
            {
                "if": {"properties": {"stageId": {"const": 0}}, "required": ["stageId"]},
                "then": {"properties": {"measurements": {"$ref": "#/$defs/profileX"}}},
            }
        ],
    }
    with pytest.raises(ValueError):
        profiles_from_schema(schema)


def test_schema_is_valid_json_schema_draft_2020_12() -> None:
    jsonschema = pytest.importorskip("jsonschema")
    schema = json.loads(default_profile_schema_path().read_text(encoding="utf-8"))
    jsonschema.Draft202012Validator.check_schema(schema)
