"""Deterministyczne generatory pomiarów e-bike dla etapów 0..3 (T009).

Ten sam seed i te same dane wejściowe dają identyczny raport (wartość nominalna
z tolerancją). Tryb `force_qa_fail` generuje pomiar łamiący regułę `qaPassed`
swojego etapu, ale nadal mieszczący się w profilu — to poprawny raport z wynikiem
negatywnym (`202`/`REJECTED`), a nie naruszenie profilu (`422`).

Reguły `qaPassed` pochodzą z `docs/product-scenario.md` pkt 2; profile (klucze,
zakresy) z `shared/schemas/telemetry.schema.json` przez `profiles.py`. Timestamp
jest parametrem (seed odtwarza pomiary), bo raport musi mieć czas z testu.
"""

from __future__ import annotations

import random
from collections.abc import Mapping
from typing import Final

from supply_chain_simulator.models import Report
from supply_chain_simulator.profiles import StageProfile, load_profiles, validate_measurements

#: Wartość nominalna i dopuszczalne odchylenie (±) pól niezależnych od reguły.
MEASUREMENT_RECIPES: Final[dict[int, dict[str, tuple[int, int]]]] = {
    0: {"checklistItemsTotal": (12, 2)},
    1: {"operationsTotal": (8, 1), "cycleTimeMs": (420_000, 60_000)},
    2: {"inspectedUnits": (10, 3), "checklistItemsTotal": (12, 2)},
    3: {"unitsPacked": (10, 3)},
}
#: Etapy, dla których generator potrafi wyprodukować pomiary.
SUPPORTED_STAGES: Final[tuple[int, ...]] = tuple(sorted(MEASUREMENT_RECIPES))


def qa_passed(stage_id: int, measurements: Mapping[str, int]) -> bool:
    """Ocenia `qaPassed` według reguł z `docs/product-scenario.md` pkt 2."""
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
    raise ValueError(f"nieznany etap: {stage_id}")


def generate_measurements(
    stage_id: int,
    seed: int,
    *,
    force_qa_fail: bool = False,
    profile: StageProfile | None = None,
) -> dict[str, int]:
    """Zwraca deterministyczne pomiary etapu `stage_id` dla danego `seed`."""
    if stage_id not in MEASUREMENT_RECIPES:
        raise ValueError(f"nieznany etap: {stage_id}")
    stage_profile = profile if profile is not None else load_profiles()[stage_id]
    rng = _stage_random(seed, stage_id)
    values: dict[str, int] = {}
    for key, (nominal, tolerance) in MEASUREMENT_RECIPES[stage_id].items():
        delta = rng.randint(-tolerance, tolerance) if tolerance else 0
        field = stage_profile.fields[key]
        values[key] = _clamp(nominal + delta, field.minimum, field.maximum)
    _fill_rule_dependent(stage_id, values, force_qa_fail)
    validate_measurements(stage_profile, values)
    return values


def generate_report(
    stage_id: int,
    *,
    batch_id: str,
    station_address: str,
    timestamp: int,
    seed: int,
    force_qa_fail: bool = False,
) -> Report:
    """Buduje raport v1 z wygenerowanych pomiarów; `qaPassed` liczy reguła etapu."""
    if stage_id not in MEASUREMENT_RECIPES:
        raise ValueError(f"nieznany etap: {stage_id}")
    profile = load_profiles()[stage_id]
    measurements = generate_measurements(
        stage_id, seed, force_qa_fail=force_qa_fail, profile=profile
    )
    return Report(
        schemaVersion=1,
        batchId=batch_id,
        stageId=stage_id,
        stationAddress=station_address,
        timestamp=timestamp,
        measurements=measurements,
        qaPassed=qa_passed(stage_id, measurements),
    )


def _stage_random(seed: int, stage_id: int) -> random.Random:
    """Niezależny strumień losowy etapu: wynik nie zależy od kolejności wywołań."""
    if isinstance(seed, bool) or not isinstance(seed, int) or seed < 0:
        raise ValueError("seed musi być nieujemną liczbą całkowitą")
    return random.Random((seed << 8) | (stage_id & 0xFF))


def _clamp(value: int, minimum: int, maximum: int) -> int:
    return max(minimum, min(maximum, value))


def _fill_rule_dependent(stage_id: int, values: dict[str, int], force_qa_fail: bool) -> None:
    """Uzupełnia pola wynikające z reguły `qaPassed` (spójne lub łamiące ją)."""
    if stage_id == 0:
        total = values["checklistItemsTotal"]
        values["checklistItemsPassed"] = total if not force_qa_fail else max(0, total - 1)
    elif stage_id == 1:
        total = values["operationsTotal"]
        values["operationsCompleted"] = total if not force_qa_fail else max(0, total - 1)
    elif stage_id == 2:
        total = values["checklistItemsTotal"]
        values["checklistItemsPassed"] = total
        values["defectsFound"] = 0 if not force_qa_fail else 1
    elif stage_id == 3:
        packed = values["unitsPacked"]
        values["labelsApplied"] = packed if not force_qa_fail else max(0, packed - 1)
