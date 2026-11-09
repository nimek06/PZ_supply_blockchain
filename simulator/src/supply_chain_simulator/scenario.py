"""Scenariusze YAML symulatora (T020, tydzień 2).

Scenariusz opisuje przebieg jednej partii: kolejne etapy, tryb (zgodny albo
wynik negatywny) i oczekiwany wynik każdego kroku. Pliki są danymi - bez kluczy,
bez adresów stacji i bez wykonywalnego kodu (`scenarios/README.md`). Adres
backendu, RPC, klucz stacji i certyfikat pochodzą z konfiguracji/CLI.

Reguły: `stageId` bez przeskakiwania i bez powtórzeń (kolejne 0,1,...),
`batchNumber` w formacie `B-RRRR-NNNN`, seed nieujemny. `qaPassed=false` zgodny
z profilem to oczekiwany `outcome=REJECTED` (202), nie błąd scenariusza.
"""

from __future__ import annotations

from pathlib import Path
from typing import Annotated, Literal

import yaml
from pydantic import Field, StringConstraints, ValidationError, model_validator

from supply_chain_simulator.canonical import keccak256_text
from supply_chain_simulator.models import SafeInt, StageId, StrictModel

BATCH_NUMBER_PATTERN = r"^B-\d{4}-\d{4}$"
BatchNumber = Annotated[str, StringConstraints(pattern=BATCH_NUMBER_PATTERN)]
NonEmptyText = Annotated[str, StringConstraints(min_length=1)]


class ScenarioError(ValueError):
    """Scenariusz nie nadaje się do uruchomienia (fail-closed)."""


class StepExpectation(StrictModel):
    """Oczekiwany wynik jednego kroku: status HTTP i opcjonalny wynik polecenia.

    `outcome` pomijamy tam, gdzie polecenie nie osiąga `CONFIRMED` (brak workera
    i relayera) - wtedy wystarcza `202` z `POST stages`. Gdy `outcome` jest
    podane, runner czeka na status terminalny i porównuje wynik (tygodnie z workerem).
    """

    httpStatus: Annotated[int, Field(ge=100, le=599)] = 202
    outcome: Literal["ACCEPTED", "REJECTED"] | None = None


class ScenarioStep(StrictModel):
    """Jeden krok: etap, opcjonalny wynik negatywny i oczekiwanie."""

    stageId: StageId
    forceQaFail: bool = False
    expect: StepExpectation


class StationReference(StrictModel):
    """Stacja scenariusza: referencja; `stationId = keccak256(reference)`."""

    reference: NonEmptyText


class Scenario(StrictModel):
    """Cały scenariusz jednej partii."""

    scenarioVersion: Literal[1]
    name: NonEmptyText
    description: str | None = None
    batchNumber: BatchNumber
    station: StationReference
    seed: SafeInt
    steps: Annotated[list[ScenarioStep], Field(min_length=1)]

    @model_validator(mode="after")
    def _stages_are_sequential(self) -> Scenario:
        expected = 0
        for step in self.steps:
            if step.stageId != expected:
                raise ValueError(
                    f"etapy muszą być kolejne od 0; oczekiwano {expected}, jest {step.stageId}"
                )
            expected += 1
        return self

    def batch_id(self) -> str:
        """`batchId = keccak256(utf8(batchNumber))` (V2-11, DEC-12)."""
        return keccak256_text(self.batchNumber)


def default_scenarios_directory() -> Path:
    """Katalog `simulator/scenarios/` w tym repozytorium."""
    return Path(__file__).resolve().parents[2] / "scenarios"


def load_scenario(path: Path | str) -> Scenario:
    """Wczytuje i waliduje scenariusz z pliku YAML."""
    source = Path(path)
    if not source.is_file():
        raise ScenarioError(f"brak pliku scenariusza: {source}")
    try:
        data = yaml.safe_load(source.read_text(encoding="utf-8"))
    except yaml.YAMLError as exc:
        raise ScenarioError(f"niepoprawny YAML w {source}: {exc}") from exc
    if not isinstance(data, dict):
        raise ScenarioError("scenariusz musi być mapowaniem YAML")
    try:
        return Scenario(**data)
    except ValidationError as exc:
        raise ScenarioError(f"scenariusz {source} niezgodny z modelem: {exc}") from exc
