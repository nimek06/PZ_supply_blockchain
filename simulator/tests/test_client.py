"""Testy klienta HTTP i budowy zgłoszenia etapu (szkic klienta, T010).

Testy nie używają sieci: transport jest atrapą (`httpx.MockTransport`), a zegar
retry wstrzykujemy, więc testy nie śpią. Sprawdzają metodę, ścieżkę, nagłówki,
treść JSON, liczbę żądań oraz to, że retry wysyła **identyczne bajty**
(brak nonce stacji, `docs/protocol.md`).
"""

from __future__ import annotations

import json

import httpx
import pytest
from pydantic import ValidationError
from supply_chain_simulator.canonical import report_hash
from supply_chain_simulator.client import (
    DEFAULT_MAX_BACKOFF_SECONDS,
    DEFAULT_BACKOFF_SECONDS,
    ApiError,
    SimulatorApiClient,
    StageSubmission,
    SubmissionError,
    build_stage_submission,
)
from supply_chain_simulator.models import Report
from supply_chain_simulator.vector import (
    ANVIL_ADDRESS_2,
    VECTOR_DEADLINE,
    load_certificate_fixture,
    signed_stage_approval,
    signed_station_completion,
    vector_domain,
    vector_report,
)

BASE_URL = "https://backend.test"
BATCH_ID = vector_report().batchId
STAGES_PATH = f"/api/v1/batches/{BATCH_ID}/stages"
COMMAND_ID = "6f1b0f0e-6a5f-4a1f-9f8f-2b9c1d2e3f40"


class FakeBackend:
    """Atrapa backendu: zapisuje żądania i zwraca przygotowane odpowiedzi.

    Kolejne wywołania dostają kolejne odpowiedzi z listy; ostatnia powtarza się
    dalej, żeby dało się sprawdzić limit prób. `Exception` na liście jest
    podnoszony (timeout transportu bez sieci).
    """

    def __init__(self, *responses: httpx.Response | Exception) -> None:
        if not responses:
            raise ValueError("potrzebna co najmniej jedna odpowiedź")
        self.requests: list[httpx.Request] = []
        self.sleeps: list[float] = []
        self._responses = list(responses)

    def __call__(self, request: httpx.Request) -> httpx.Response:
        self.requests.append(request)
        index = min(len(self.requests) - 1, len(self._responses) - 1)
        response = self._responses[index]
        if isinstance(response, Exception):
            raise response
        return response

    @property
    def bodies(self) -> list[bytes]:
        """Treści kolejnych żądań - dowód, że retry nie zmienia bajtów."""
        return [request.content for request in self.requests]

    def client(self, **overrides: object) -> SimulatorApiClient:
        """Klient wskazujący na atrapę: bez sieci i bez prawdziwego snu."""
        return SimulatorApiClient(
            BASE_URL,
            token="test-token",
            client=httpx.Client(transport=httpx.MockTransport(self)),
            sleep=self.sleeps.append,
            **overrides,
        )


def accepted(command_id: str = COMMAND_ID) -> httpx.Response:
    """Odpowiedź `202 CommandAccepted`."""
    return httpx.Response(
        202,
        json={"commandId": command_id, "statusUrl": f"/api/v1/commands/{command_id}"},
    )


def problem(status: int, code: str, detail: str = "szczegół błędu") -> httpx.Response:
    """Odpowiedź błędu jako `application/problem+json`."""
    return httpx.Response(
        status,
        json={
            "type": f"https://errors.test/{code}",
            "title": "Blad domenowy",
            "status": status,
            "code": code,
            "detail": detail,
            "correlationId": "corr-123",
        },
        headers={"Content-Type": "application/problem+json"},
    )


def command_status(status: str = "CONFIRMED", **extra: object) -> httpx.Response:
    """Odpowiedź `200 CommandStatus`."""
    return httpx.Response(200, json={"commandId": COMMAND_ID, "status": status, **extra})


def submission(deadline: int = VECTOR_DEADLINE) -> StageSubmission:
    """Zgłoszenie etapu zbudowane z wektora podpisu (stacja Anvil #1)."""
    report = vector_report()
    return build_stage_submission(
        report,
        load_certificate_fixture(),
        deadline=deadline,
        signed=signed_station_completion(vector_domain(), report),
    )


def test_submit_stage_sends_signed_report_once() -> None:
    backend = FakeBackend(accepted())
    with backend.client() as client:
        result = client.submit_stage(BATCH_ID, submission())

    assert result.commandId == COMMAND_ID
    assert len(backend.requests) == 1

    request = backend.requests[0]
    assert request.method == "POST"
    assert str(request.url) == BASE_URL + STAGES_PATH
    assert request.headers["Authorization"] == "Bearer test-token"
    assert request.headers["Content-Type"] == "application/json"

    body = json.loads(backend.bodies[0])
    assert set(body) == {"report", "stationCertificateId", "reportHash", "deadline", "signature"}
    assert body["report"] == vector_report().model_dump(mode="json")
    assert body["reportHash"] == report_hash(vector_report())
    assert body["deadline"] == VECTOR_DEADLINE
    assert body["signature"] == submission().signature
    # W treści żądania nie ma klucza prywatnego ani pliku klucza stacji.
    assert "privateKey" not in backend.bodies[0].decode("utf-8")


def test_submit_stage_rejects_path_batch_id_mismatch() -> None:
    backend = FakeBackend(accepted())
    with pytest.raises(SubmissionError):
        backend.client().submit_stage("0x" + "11" * 32, submission())
    assert backend.requests == []


def test_submit_stage_rejects_invalid_path_batch_id() -> None:
    backend = FakeBackend(accepted())
    with pytest.raises(ValueError):
        backend.client().submit_stage("B-2026-0001", submission())
    assert backend.requests == []


def test_retry_after_unavailable_repeats_identical_bytes() -> None:
    backend = FakeBackend(problem(503, "TEMPORARY_UNAVAILABLE"), accepted())
    with backend.client() as client:
        result = client.submit_stage(BATCH_ID, submission())

    assert result.commandId == COMMAND_ID
    assert len(backend.requests) == 2
    assert backend.bodies[0] == backend.bodies[1]
    assert backend.sleeps == [DEFAULT_BACKOFF_SECONDS]


def test_retry_honours_retry_after_header() -> None:
    limited = problem(429, "RATE_LIMITED")
    limited.headers["Retry-After"] = "2"
    backend = FakeBackend(limited, accepted())

    with backend.client() as client:
        client.submit_stage(BATCH_ID, submission())

    assert backend.sleeps == [2.0]
    assert len(backend.requests) == 2


def test_retry_delay_is_capped_by_max_backoff() -> None:
    limited = problem(429, "RATE_LIMITED")
    limited.headers["Retry-After"] = "999"
    backend = FakeBackend(limited, accepted())

    with backend.client() as client:
        client.submit_stage(BATCH_ID, submission())

    assert backend.sleeps == [DEFAULT_MAX_BACKOFF_SECONDS]


def test_retry_stops_after_max_attempts() -> None:
    backend = FakeBackend(problem(503, "TEMPORARY_UNAVAILABLE"))
    with pytest.raises(ApiError) as excinfo:
        backend.client(max_attempts=3).submit_stage(BATCH_ID, submission())

    error = excinfo.value
    assert error.status == 503
    assert error.code == "TEMPORARY_UNAVAILABLE"
    assert error.correlation_id == "corr-123"
    assert len(backend.requests) == 3
    assert len(backend.sleeps) == 2
    assert "TEMPORARY_UNAVAILABLE" in str(error)
    assert "trwale" in str(error)


def test_timeout_is_retried_with_same_bytes_and_no_new_report() -> None:
    timeout = httpx.TimeoutException("timeout transportu")
    backend = FakeBackend(timeout, timeout, timeout)

    with pytest.raises(ApiError) as excinfo:
        backend.client(max_attempts=3).submit_stage(BATCH_ID, submission())

    assert len(backend.requests) == 3
    assert len({body for body in backend.bodies}) == 1
    message = str(excinfo.value)
    assert "timeout" in message
    assert "identyczna" in message
    assert excinfo.value.status is None


def test_station_not_active_is_not_retried() -> None:
    backend = FakeBackend(problem(403, "STATION_NOT_ACTIVE"))

    with pytest.raises(ApiError) as excinfo:
        backend.client().submit_stage(BATCH_ID, submission())

    assert excinfo.value.status == 403
    assert excinfo.value.code == "STATION_NOT_ACTIVE"
    assert len(backend.requests) == 1
    assert backend.sleeps == []


def test_stage_already_pending_explains_the_rule() -> None:
    backend = FakeBackend(problem(409, "STAGE_ALREADY_PENDING"))

    with pytest.raises(ApiError) as excinfo:
        backend.client().submit_stage(BATCH_ID, submission())

    assert excinfo.value.status == 409
    assert "aktywne polecenie" in str(excinfo.value)
    assert len(backend.requests) == 1


def test_invalid_submission_is_rejected_before_request() -> None:
    backend = FakeBackend(accepted())

    with pytest.raises(ValidationError):
        StageSubmission(
            report=vector_report(),
            stationCertificateId="0x" + "22" * 32,
            reportHash=report_hash(vector_report()),
            deadline=VECTOR_DEADLINE,
            signature="0x" + "33" * 65,
            extraField=1,
        )
    assert backend.requests == []


def test_get_command_reads_status_from_protocol_path() -> None:
    backend = FakeBackend(command_status("READY_TO_SUBMIT", retryAllowedAfter=1_800_000_700))

    with backend.client() as client:
        status = client.get_command(COMMAND_ID)

    assert status.commandId == COMMAND_ID
    assert status.status == "READY_TO_SUBMIT"
    assert status.retryAllowedAfter == 1_800_000_700
    assert status.is_terminal() is False
    assert status.is_confirmed() is False
    assert status.is_rejected() is False

    request = backend.requests[0]
    assert request.method == "GET"
    assert str(request.url) == f"{BASE_URL}/api/v1/commands/{COMMAND_ID}"


def test_confirmed_rejected_command_is_terminal_and_does_not_move_batch() -> None:
    backend = FakeBackend(command_status("CONFIRMED", outcome="REJECTED"))

    with backend.client() as client:
        status = client.get_command(COMMAND_ID)

    assert status.is_terminal() is True
    assert status.is_confirmed() is True
    assert status.is_rejected() is True


def test_unknown_command_status_is_rejected() -> None:
    backend = FakeBackend(command_status("CANCELLED"))

    with pytest.raises(ApiError) as excinfo:
        backend.client().get_command(COMMAND_ID)

    assert excinfo.value.status == 200
    assert "niezgodna z modelem" in str(excinfo.value)
    assert len(backend.requests) == 1


def test_missing_command_returns_not_found_error() -> None:
    backend = FakeBackend(problem(404, "COMMAND_NOT_FOUND"))

    with pytest.raises(ApiError) as excinfo:
        backend.client().get_command(COMMAND_ID)

    assert excinfo.value.status == 404
    assert excinfo.value.code == "COMMAND_NOT_FOUND"


def test_command_id_with_slash_is_rejected() -> None:
    backend = FakeBackend(command_status())

    with pytest.raises(ValueError):
        backend.client().get_command("../commands/1")
    assert backend.requests == []


def test_extra_response_fields_do_not_break_the_client() -> None:
    response = httpx.Response(
        202,
        json={
            "commandId": COMMAND_ID,
            "status": "ACCEPTED",
            "statusUrl": f"/api/v1/commands/{COMMAND_ID}",
            "correlationId": "corr-999",
            "nowePole": {"przyszle": True},
        },
    )
    backend = FakeBackend(response)

    with backend.client() as client:
        result = client.submit_stage(BATCH_ID, submission())

    assert result.commandId == COMMAND_ID
    assert result.statusUrl == f"/api/v1/commands/{COMMAND_ID}"


def test_command_status_url_uses_protocol_path() -> None:
    backend = FakeBackend(accepted())
    client = backend.client()

    assert client.base_url == BASE_URL
    assert client.command_status_url(COMMAND_ID) == f"{BASE_URL}/api/v1/commands/{COMMAND_ID}"
    assert SimulatorApiClient("https://backend.test/").base_url == BASE_URL


def test_invalid_client_arguments_are_rejected() -> None:
    with pytest.raises(ValueError):
        SimulatorApiClient("localhost:8080")
    with pytest.raises(ValueError):
        SimulatorApiClient(BASE_URL, token="   ")
    with pytest.raises(ValueError):
        SimulatorApiClient(BASE_URL, timeout=0)
    with pytest.raises(ValueError):
        SimulatorApiClient(BASE_URL, max_attempts=0)
    with pytest.raises(ValueError):
        SimulatorApiClient(BASE_URL, backoff_seconds=-1)


def test_close_leaves_injected_client_usable() -> None:
    backend = FakeBackend(command_status())
    inner = httpx.Client(transport=httpx.MockTransport(backend))
    client = SimulatorApiClient(BASE_URL, client=inner, sleep=backend.sleeps.append)

    client.close()
    client.close()

    assert inner.is_closed is False
    assert client.get_command(COMMAND_ID).commandId == COMMAND_ID


def test_build_stage_submission_matches_signed_intent() -> None:
    report = vector_report()
    certificate = load_certificate_fixture()
    signed = signed_station_completion(vector_domain(), report)

    built = build_stage_submission(report, certificate, deadline=VECTOR_DEADLINE, signed=signed)

    assert built.report is report
    assert built.reportHash == report_hash(report)
    assert built.stationCertificateId == certificate.payload.certificateId
    assert built.signature == signed.signature
    assert built.deadline == VECTOR_DEADLINE


def test_build_stage_submission_rejects_approval_signature() -> None:
    report = vector_report()
    signed = signed_stage_approval(vector_domain(), report)

    with pytest.raises(SubmissionError, match="StageCompletion"):
        build_stage_submission(
            report, load_certificate_fixture(), deadline=VECTOR_DEADLINE, signed=signed
        )


def test_build_stage_submission_rejects_report_changed_after_signing() -> None:
    report = vector_report()
    signed = signed_station_completion(vector_domain(), report)
    tampered = Report(
        **{**report.model_dump(mode="json"), "measurements": {"temperatureMilliC": 99}}
    )

    with pytest.raises(SubmissionError, match="reportHash"):
        build_stage_submission(
            tampered, load_certificate_fixture(), deadline=VECTOR_DEADLINE, signed=signed
        )


def test_build_stage_submission_rejects_other_deadline() -> None:
    report = vector_report()
    signed = signed_station_completion(vector_domain(), report)

    with pytest.raises(SubmissionError, match="deadline"):
        build_stage_submission(
            report,
            load_certificate_fixture(),
            deadline=VECTOR_DEADLINE + 60,
            signed=signed,
        )


def test_build_stage_submission_rejects_stage_outside_certificate() -> None:
    report = vector_report()
    signed = signed_station_completion(vector_domain(), report)
    certificate = load_certificate_fixture()
    narrowed = certificate.model_copy(
        update={"payload": certificate.payload.model_copy(update={"allowedStageIds": [0, 1]})}
    )

    with pytest.raises(SubmissionError, match="dozwolony"):
        build_stage_submission(report, narrowed, deadline=VECTOR_DEADLINE, signed=signed)


def test_build_stage_submission_rejects_certificate_of_other_station() -> None:
    report = vector_report()
    signed = signed_station_completion(vector_domain(), report)
    certificate = load_certificate_fixture()
    other_station = certificate.model_copy(
        update={
            "payload": certificate.payload.model_copy(
                update={"stationAddress": ANVIL_ADDRESS_2.lower()}
            )
        }
    )

    with pytest.raises(SubmissionError, match="innej stacji"):
        build_stage_submission(report, other_station, deadline=VECTOR_DEADLINE, signed=signed)
