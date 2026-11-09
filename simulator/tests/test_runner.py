"""Testy runnera scenariusza (T020): tryb accept-only (202) i tryb z `outcome`.

Klient HTTP działa na `httpx.MockTransport`. W trybie tygodnia 2 (`outcome`
pominięty) runner kończy krok po `202` i NIE czyta statusu polecenia; gdy
`outcome` jest podane, czeka na status terminalny i porównuje wynik.
"""

from __future__ import annotations

import json
from pathlib import Path

import httpx
import pytest
from supply_chain_simulator.certificate import sign_certificate
from supply_chain_simulator.chain import BatchState
from supply_chain_simulator.client import SimulatorApiClient
from supply_chain_simulator.config import SimulatorConfig
from supply_chain_simulator.identity import StationKeyFile
from supply_chain_simulator.runner import Runner, RunnerError
from supply_chain_simulator.scenario import Scenario, default_scenarios_directory, load_scenario
from supply_chain_simulator.vector import (
    ANVIL_ADDRESS_1,
    ANVIL_KEY_0,
    ANVIL_KEY_1,
    VECTOR_CHAIN_ID,
    VECTOR_CONTRACT,
    certificate_payload,
)

BACKEND_URL = "http://127.0.0.1:8080"
RPC_URL = "http://127.0.0.1:8545"
FIXED_NOW = 1_800_000_000


def scenario(name: str) -> Scenario:
    return load_scenario(default_scenarios_directory() / f"{name}.yaml")


def scenario_with_outcome(*, force_fail: bool, outcome: str) -> Scenario:
    return Scenario(
        **{
            "scenarioVersion": 1,
            "name": "outcome-path",
            "batchNumber": "B-2026-0001",
            "station": {"reference": "ST-001"},
            "seed": 1,
            "steps": [
                {
                    "stageId": 0,
                    "forceQaFail": force_fail,
                    "expect": {"httpStatus": 202, "outcome": outcome},
                }
            ],
        }
    )


def config() -> SimulatorConfig:
    return SimulatorConfig(
        backend_url=BACKEND_URL,
        rpc_url=RPC_URL,
        chain_id=VECTOR_CHAIN_ID,
        verifying_contract=VECTOR_CONTRACT,
        station_key_path=Path("/tmp/station-key.json"),
        certificate_path=Path("/tmp/station-certificate.json"),
        seed=1,
    )


def station_key() -> StationKeyFile:
    return StationKeyFile(
        address=ANVIL_ADDRESS_1, private_key=ANVIL_KEY_1, path=Path("/tmp/station-key.json")
    )


def make_api(*, terminal_status: str | None = "CONFIRMED") -> tuple[SimulatorApiClient, dict]:
    """Klient bez sieci; `outcome` zależy od `qaPassed` z body; zwraca licznik żądań."""
    state: dict = {"posts": 0, "gets": 0, "qa": {}}

    def handler(request: httpx.Request) -> httpx.Response:
        if request.method == "POST":
            body = json.loads(request.content)
            state["posts"] += 1
            command_id = f"cmd-{state['posts']}"
            state["qa"][command_id] = bool(body["report"]["qaPassed"])
            return httpx.Response(202, json={"commandId": command_id, "status": "ACCEPTED"})
        state["gets"] += 1
        command_id = request.url.path.rsplit("/", 1)[-1]
        if terminal_status is None:
            return httpx.Response(200, json={"commandId": command_id, "status": "PINNING"})
        if terminal_status == "FAILED":
            return httpx.Response(
                200, json={"commandId": command_id, "status": "FAILED", "errorCode": "REVERT"}
            )
        outcome = "ACCEPTED" if state["qa"].get(command_id) else "REJECTED"
        return httpx.Response(
            200, json={"commandId": command_id, "status": "CONFIRMED", "outcome": outcome}
        )

    client = SimulatorApiClient(
        BACKEND_URL, client=httpx.Client(transport=httpx.MockTransport(handler))
    )
    return client, state


class FakeChain:
    """Atrapa czytnika łańcucha: zwraca zadany `nextStage`, bez sieci."""

    def __init__(self, next_stage: int) -> None:
        self._next_stage = next_stage

    def get_batch(self, batch_id: str) -> BatchState:
        return BatchState(True, self._next_stage, False)

    def close(self) -> None:  # pragma: no cover - zgodność z interfejsem
        pass


def make_runner(
    active: Scenario, *, chain: object | None = None, terminal_status: str | None = "CONFIRMED"
) -> tuple[Runner, dict]:
    certificate, _ = sign_certificate(certificate_payload(), ANVIL_KEY_0)
    api, state = make_api(terminal_status=terminal_status)
    runner = Runner(
        config=config(),
        scenario=active,
        station_key=station_key(),
        certificate=certificate,
        api=api,
        chain=chain,
        clock=lambda: FIXED_NOW,
        poll_interval=0.0,
        sleep=lambda seconds: None,
    )
    return runner, state


def test_happy_path_accepts_and_does_not_read_commands() -> None:
    runner, state = make_runner(scenario("ebike-happy-path"))
    result = runner.run()
    assert result.outcomes == (None, None)  # tydzień 2: brak outcome
    assert [step.qa_passed for step in result.steps] == [True, True]
    assert [step.stage_id for step in result.steps] == [0, 1]
    assert all(step.status == "ACCEPTED" for step in result.steps)
    assert state == {"posts": 2, "gets": 0, "qa": state["qa"]}
    assert all(step.next_stage is None for step in result.steps)


def test_qa_fail_scenario_accepts_without_reading_commands() -> None:
    runner, state = make_runner(scenario("ebike-qa-fail"))
    result = runner.run()
    assert result.outcomes == (None, None)
    assert result.steps[1].qa_passed is False
    assert state["gets"] == 0


def test_accept_only_does_not_need_a_worker() -> None:
    """Nawet gdy status nigdy nie jest terminalny, accept-only kończy się sukcesem."""
    runner, _ = make_runner(scenario("ebike-happy-path"), terminal_status=None)
    assert runner.run().outcomes == (None, None)


def test_batch_id_comes_from_the_scenario() -> None:
    runner, _ = make_runner(scenario("ebike-happy-path"))
    assert runner.run().batch_id == scenario("ebike-happy-path").batch_id()


def test_chain_read_records_next_stage() -> None:
    runner, _ = make_runner(scenario("ebike-happy-path"), chain=FakeChain(2))
    assert [step.next_stage for step in runner.run().steps] == [2, 2]


def test_outcome_path_waits_for_confirmed() -> None:
    runner, state = make_runner(scenario_with_outcome(force_fail=False, outcome="ACCEPTED"))
    result = runner.run()
    assert result.outcomes == ("ACCEPTED",)
    assert result.steps[0].status == "CONFIRMED"
    assert state["gets"] >= 1


def test_expectation_mismatch_is_rejected() -> None:
    runner, _ = make_runner(scenario_with_outcome(force_fail=True, outcome="ACCEPTED"))
    with pytest.raises(RunnerError):
        runner.run()


def test_failed_command_is_rejected() -> None:
    runner, _ = make_runner(
        scenario_with_outcome(force_fail=False, outcome="ACCEPTED"), terminal_status="FAILED"
    )
    with pytest.raises(RunnerError):
        runner.run()


def test_exhausted_polling_is_rejected() -> None:
    runner, _ = make_runner(
        scenario_with_outcome(force_fail=False, outcome="ACCEPTED"), terminal_status=None
    )
    with pytest.raises(RunnerError):
        runner.run()


def test_non_202_expectation_is_unsupported() -> None:
    data = {
        "scenarioVersion": 1,
        "name": "wrong-status",
        "batchNumber": "B-2026-0001",
        "station": {"reference": "ST-001"},
        "seed": 1,
        "steps": [{"stageId": 0, "expect": {"httpStatus": 422}}],
    }
    runner, _ = make_runner(Scenario(**data))
    with pytest.raises(RunnerError):
        runner.run()
