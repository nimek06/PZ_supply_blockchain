"""Profile pomiarów etapów 0..3 czytane ze schematu (T009).

Profil e-bike v1 jest źródłem prawdy w `shared/schemas/telemetry.schema.json`
(`docs/product-scenario.md`, DEC-02). Ten moduł czyta schemat i buduje z niego
`StageProfile` (etap -> nazwa profilu, pola, zakresy), więc generator nie powtarza
zakresów w kodzie: zmiana schematu automatycznie zmienia dopuszczalne wartości.

Reguły `qaPassed` są semantyczne (JSON Schema ich nie wyraża), dlatego mieszkają
w `telemetry.py`; tutaj zostaje wyłącznie format: klucze wymagane,
`additionalProperties: false` i całkowite zakresy min/max.
"""

from __future__ import annotations

import json
from collections.abc import Mapping
from dataclasses import dataclass
from pathlib import Path
from typing import Any

PROFILE_SCHEMA_FILENAME = "telemetry.schema.json"


@dataclass(frozen=True)
class ProfileField:
    """Jedno pole profilu: nazwa i dopuszczalny zakres całkowity (domknięty)."""

    name: str
    minimum: int
    maximum: int


@dataclass(frozen=True)
class StageProfile:
    """Profil jednego etapu: nazwa definicji, wymagane klucze i ich zakresy."""

    stage_id: int
    name: str
    fields: Mapping[str, ProfileField]
    required: tuple[str, ...]


def default_profile_schema_path() -> Path:
    """Ścieżka `shared/schemas/telemetry.schema.json` w tym repozytorium."""
    return (
        Path(__file__).resolve().parents[3]
        / "shared"
        / "schemas"
        / PROFILE_SCHEMA_FILENAME
    )


def load_profiles(path: Path | None = None) -> dict[int, StageProfile]:
    """Wczytuje schemat i zwraca mapę `stageId -> StageProfile`."""
    source = path if path is not None else default_profile_schema_path()
    schema = json.loads(source.read_text(encoding="utf-8"))
    return profiles_from_schema(schema)


def profiles_from_schema(schema: Mapping[str, Any]) -> dict[int, StageProfile]:
    """Buduje profile z już sparsowanego schematu (bez IO)."""
    defs = schema.get("$defs")
    if not isinstance(defs, Mapping):
        raise ValueError("schemat telemetrii nie ma $defs z profilami")
    names = _stage_profile_names(schema)
    return {stage: _build_profile(stage, name, defs) for stage, name in names.items()}


def validate_measurements(profile: StageProfile, measurements: Mapping[str, int]) -> None:
    """Sprawdza format pomiarów względem profilu (klucze, typ, zakres).

    Podnosi `ValueError`; nie ocenia `qaPassed` (to reguła w `telemetry.py`).
    """
    keys = set(measurements)
    required = set(profile.required)
    if keys != required:
        raise ValueError(
            f"etap {profile.stage_id}: klucze pomiarów {sorted(keys)} "
            f"nie odpowiadają wymaganym {sorted(required)}"
        )
    for key in profile.required:
        value = measurements[key]
        field = profile.fields[key]
        if isinstance(value, bool) or not isinstance(value, int):
            raise ValueError(
                f"etap {profile.stage_id}: {key} musi być liczbą całkowitą, jest {value!r}"
            )
        if not field.minimum <= value <= field.maximum:
            raise ValueError(
                f"etap {profile.stage_id}: {key}={value} poza zakresem "
                f"{field.minimum}..{field.maximum}"
            )


def _stage_profile_names(schema: Mapping[str, Any]) -> dict[int, str]:
    """Mapuje `stageId -> nazwa definicji` z sekcji `allOf` (if/then)."""
    mapping: dict[int, str] = {}
    for entry in schema.get("allOf", ()):
        condition = entry.get("if", {}).get("properties", {})
        stage = condition.get("stageId", {}).get("const")
        ref = entry.get("then", {}).get("properties", {}).get("measurements", {}).get("$ref")
        if stage is None or not isinstance(ref, str):
            raise ValueError("sekcja allOf musi mapować stageId na $ref profilu")
        mapping[int(stage)] = ref.rsplit("/", 1)[-1]
    if not mapping:
        raise ValueError("schemat nie definiuje profili etapów w allOf")
    return dict(sorted(mapping.items()))


def _resolve(defs: Mapping[str, Any], spec: Any) -> Mapping[str, Any]:
    """Rozwija lokalny `$ref` (`#/$defs/...`); inne referencje odrzuca."""
    seen: set[str] = set()
    while isinstance(spec, Mapping) and "$ref" in spec:
        ref = spec["$ref"]
        if not isinstance(ref, str) or not ref.startswith("#/$defs/"):
            raise ValueError(f"nieobsługiwany $ref w profilu: {ref!r}")
        name = ref.rsplit("/", 1)[-1]
        if name in seen:
            raise ValueError(f"cykliczny $ref w profilu: {name}")
        seen.add(name)
        try:
            spec = defs[name]
        except KeyError as exc:
            raise ValueError(f"brak definicji $defs/{name}") from exc
    if not isinstance(spec, Mapping):
        raise ValueError("definicja profilu nie jest obiektem")
    return spec


def _build_profile(stage_id: int, name: str, defs: Mapping[str, Any]) -> StageProfile:
    node = _resolve(defs, {"$ref": f"#/$defs/{name}"})
    if node.get("additionalProperties") is not False:
        raise ValueError(f"profil {name}: measurements musi mieć additionalProperties=false")
    properties = node.get("properties")
    if not isinstance(properties, Mapping) or not properties:
        raise ValueError(f"profil {name}: brak properties")
    required = tuple(node.get("required", ()))
    fields: dict[str, ProfileField] = {}
    for key, raw in properties.items():
        spec = _resolve(defs, raw)
        if spec.get("type") != "integer":
            raise ValueError(f"profil {name}: pole {key} musi być integer")
        minimum, maximum = spec.get("minimum"), spec.get("maximum")
        if not isinstance(minimum, int) or not isinstance(maximum, int):
            raise ValueError(f"profil {name}: pole {key} musi mieć minimum i maximum")
        fields[key] = ProfileField(key, minimum, maximum)
    missing = [key for key in required if key not in fields]
    if missing:
        raise ValueError(f"profil {name}: wymagane pola bez definicji: {missing}")
    return StageProfile(stage_id, name, fields, required)
