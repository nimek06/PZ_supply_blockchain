"""Uruchamianie scenariusza: raport -> podpis -> backend -> stan (T020).

Kolejność kroku: wygeneruj deterministycznie raport (`telemetry`), podpisz
`StageCompletion` kluczem stacji, zbuduj zgłoszenie i wyślij `POST stages` dla
partii ze scenariusza, poczekaj na status polecenia i porównaj wynik z
oczekiwaniem. Na końcu opcjonalnie odczytaj stan partii przez RPC (`getBatch`,
DEC-10). `sleep`, klient API, czytnik łańcucha i zegar są wstrzykiwane, więc
testy nie używają sieci.

Zasada z `corrections.md`: `qaPassed=false` zgodne z profilem to `202` i
`outcome=REJECTED` (partia się nie przesuwa), a nie błąd uruchomienia.
"""

from __future__ import annotations

import time
from collections.abc import Callable
from dataclasses import dataclass

from supply_chain_simulator.canonical import report_hash
from supply_chain_simulator.chain import ChainReader
from supply_chain_simulator.client import CommandStatus, SimulatorApiClient, build_stage_submission
from supply_chain_simulator.config import SimulatorConfig
from supply_chain_simulator.identity import StationKeyFile
from supply_chain_simulator.models import (
    Eip712Domain,
    StageCompletionMessage,
    StationCertificate,
)
from supply_chain_simulator.scenario import Scenario, ScenarioStep
from supply_chain_simulator.signing import sign_stage_completion
from supply_chain_simulator.telemetry import generate_report

DEFAULT_DEADLINE_SECONDS = 600
DEFAULT_POLL_ATTEMPTS = 6
DEFAULT_POLL_INTERVAL_SECONDS = 1.0
EXPECTED_HTTP_STATUS = 202
DOMAIN_NAME = "SupplyChain"
DOMAIN_VERSION = "1"


class RunnerError(ValueError):
    """Przebieg scenariusza odbiegł od oczekiwań (fail-closed)."""


@dataclass(frozen=True)
class StepResult:
    """Wynik jednego kroku scenariusza."""

    stage_id: int
    qa_passed: bool
    command_id: str
    status: str
    outcome: str | None
    next_stage: int | None


@dataclass(frozen=True)
class RunResult:
    """Wynik całego scenariusza."""

    scenario: str
    batch_id: str
    steps: tuple[StepResult, ...]

    @property
    def outcomes(self) -> tuple[str | None, ...]:
        """Wyniki kroków w kolejności (`ACCEPTED`/`REJECTED`)."""
        return tuple(step.outcome for step in self.steps)


class Runner:
    """Wykonuje scenariusz na lokalnym backendzie i (opcjonalnie) łańcuchu."""

    def __init__(
        self,
        *,
        config: SimulatorConfig,
        scenario: Scenario,
        station_key: StationKeyFile,
        certificate: StationCertificate,
        api: SimulatorApiClient,
        chain: ChainReader | None = None,
        clock: Callable[[], int] | None = None,
        deadline_seconds: int = DEFAULT_DEADLINE_SECONDS,
        poll_attempts: int = DEFAULT_POLL_ATTEMPTS,
        poll_interval: float = DEFAULT_POLL_INTERVAL_SECONDS,
        sleep: Callable[[float], None] = time.sleep,
    ) -> None:
        if deadline_seconds <= 0:
            raise RunnerError("deadline_seconds musi być dodatnie")
        if poll_attempts < 1:
            raise RunnerError("poll_attempts musi być co najmniej 1")
        if poll_interval < 0:
            raise RunnerError("poll_interval nie może być ujemny")
        self._config = config
        self._scenario = scenario
        self._station_key = station_key
        self._certificate = certificate
        self._api = api
        self._chain = chain
        self._clock = clock if clock is not None else (lambda: int(time.time()))
        self._deadline_seconds = deadline_seconds
        self._poll_attempts = poll_attempts
        self._poll_interval = poll_interval
        self._sleep = sleep

    def run(self) -> RunResult:
        """Wykonuje wszystkie kroki scenariusza i zwraca ich wyniki."""
        batch_id = self._scenario.batch_id()
        domain = self._domain()
        results: list[StepResult] = []

        for step in self._scenario.steps:
            if step.expect.httpStatus != EXPECTED_HTTP_STATUS:
                raise RunnerError(
                    f"etap {step.stageId}: klient obsługuje tylko status {EXPECTED_HTTP_STATUS}"
                )
            timestamp = self._clock()
            report = generate_report(
                step.stageId,
                batch_id=batch_id,
                station_address=self._station_key.address,
                timestamp=timestamp,
                seed=self._scenario.seed,
                force_qa_fail=step.forceQaFail,
            )
            deadline = timestamp + self._deadline_seconds
            signed = sign_stage_completion(
                domain,
                StageCompletionMessage(
                    batchId=batch_id,
                    stageId=step.stageId,
                    reportHash=report_hash(report),
                    qaPassed=report.qaPassed,
                    deadline=deadline,
                ),
                self._station_key.private_key,
            )
            submission = build_stage_submission(
                report, self._certificate, deadline=deadline, signed=signed
            )
            accepted = self._api.submit_stage(batch_id, submission)
            status = self._await_command(accepted.commandId)
            self._check(step, report.qaPassed, status)

            next_stage = None
            if self._chain is not None:
                next_stage = self._chain.get_batch(batch_id).next_stage

            results.append(
                StepResult(
                    stage_id=step.stageId,
                    qa_passed=report.qaPassed,
                    command_id=status.commandId,
                    status=status.status,
                    outcome=status.outcome,
                    next_stage=next_stage,
                )
            )

        return RunResult(self._scenario.name, batch_id, tuple(results))

    def _await_command(self, command_id: str) -> CommandStatus:
        """Czeka, aż polecenie będzie terminalne (`CONFIRMED`/`FAILED`)."""
        for _ in range(self._poll_attempts):
            status = self._api.get_command(command_id)
            if status.is_terminal():
                return status
            self._sleep(self._poll_interval)
        raise RunnerError(f"polecenie {command_id} nie zakończyło się w limicie prób")

    def _check(self, step: ScenarioStep, qa_passed: bool, status: CommandStatus) -> None:
        """Weryfikuje wynik backendu wobec oczekiwania scenariusza i `qaPassed`."""
        expected_outcome = "REJECTED" if not qa_passed else "ACCEPTED"
        if step.expect.outcome != expected_outcome:
            raise RunnerError(
                f"etap {step.stageId}: scenariusz oczekuje {step.expect.outcome}, "
                f"a raport ma qaPassed={qa_passed}"
            )
        if not status.is_confirmed():
            raise RunnerError(
                f"etap {step.stageId}: polecenie {status.commandId} zakończone jako "
                f"{status.status} (errorCode={status.errorCode})"
            )
        if status.outcome != expected_outcome:
            raise RunnerError(
                f"etap {step.stageId}: backend zwrócił outcome={status.outcome}, "
                f"oczekiwano {expected_outcome}"
            )

    def _domain(self) -> Eip712Domain:
        """Domena EIP-712 z konfiguracji sieci (chainId i kontrakt wdrożenia)."""
        return Eip712Domain(
            name=DOMAIN_NAME,
            version=DOMAIN_VERSION,
            chainId=self._config.chain_id,
            verifyingContract=self._config.verifying_contract,
        )
