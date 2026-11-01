"""Klient HTTP backendu: wysyłka raportu etapu i odczyt statusu polecenia.

Pola i ścieżki pochodzą z `docs/protocol.md` („Planowane API”, „Status i błędy”)
oraz ze zmian v2: brak nonce stacji, brak cookie/CSRF, uwierzytelnienie tokenem
Bearer w nagłówku `Authorization`, a `qaPassed=false` też kończy się `202`.
`409 STAGE_ALREADY_PENDING` oznacza aktywne polecenie dla `(batchId, stageId)`.

Po timeout nie wolno wymyślać nowego raportu (DEC-09 zamknięta jako wariant C
uproszczony): ponawiamy **identyczne** żądanie, dlatego retry wysyła te same
bajty i nie zależy od czasu. Kody `4xx` (poza `429`) nie są ponawiane - błędny
podpis, nieaktywna stacja i konflikt etapu nie poprawią się same.

Szkic: `shared/schemas/openapi.yaml` (T002) jeszcze nie istnieje, więc modele
odpowiedzi są odporne na rozszerzenia (`extra="allow"`): nowe pole w odpowiedzi
nie może wyłączyć symulatora. Lista statusów polecenia jest natomiast
walidowana wobec modelu stanów z `docs/data-model.md` (fail-closed).
"""

from __future__ import annotations

import re
import time
from collections.abc import Callable
from typing import Literal, TypeVar

import httpx
from pydantic import BaseModel, ConfigDict, ValidationError

from supply_chain_simulator import __version__
from supply_chain_simulator.canonical import report_hash
from supply_chain_simulator.models import (
    HEX_32_PATTERN,
    Report,
    SafeInt,
    Signature65,
    StageCompletionMessage,
    StationCertificate,
    StrictModel,
    Hex32,
    message_payload,
)
from supply_chain_simulator.signing import SignedTypedData

#: Domyślny timeout jednego żądania (sekundy).
DEFAULT_TIMEOUT_SECONDS = 10.0
#: Domyślna liczba prób wysyłki (1 + retry). Bez nieskończonego retry.
DEFAULT_MAX_ATTEMPTS = 3
#: Bazowe opóźnienie między próbami (sekundy); rośnie dwukrotnie.
DEFAULT_BACKOFF_SECONDS = 0.5
#: Górna granica opóźnienia, także dla nagłówka `Retry-After`.
DEFAULT_MAX_BACKOFF_SECONDS = 5.0

STAGES_PATH = "/api/v1/batches/{batch_id}/stages"
COMMANDS_PATH = "/api/v1/commands/{command_id}"

HTTP_OK = 200
HTTP_ACCEPTED = 202
#: Kody, które wolno ponowić identycznym żądaniem.
RETRY_STATUS_CODES = frozenset({429, 500, 502, 503, 504})

#: Statusy polecenia z `docs/data-model.md` (kolejność przepływu).
CommandState = Literal[
    "ACCEPTED",
    "PINNING",
    "WAITING_FOR_QA",
    "READY_TO_SUBMIT",
    "SUBMITTED",
    "CONFIRMED",
    "FAILED",
]
Outcome = Literal["ACCEPTED", "REJECTED"]
#: Statusy kończące życie polecenia.
TERMINAL_COMMAND_STATES = frozenset({"CONFIRMED", "FAILED"})


class ApiError(RuntimeError):
    """Błąd klienta API: odpowiedź błędu backendu albo brak trwałego przyjęcia.

    `code` to stabilny kod domenowy z `application/problem+json`, a
    `correlation_id` służy do znalezienia zdarzenia w logach backendu.
    Klasa nie zawiera treści raportu ani podpisu - te nie trafiają do logów.
    """

    def __init__(
        self,
        message: str,
        *,
        status: int | None = None,
        code: str | None = None,
        correlation_id: str | None = None,
    ) -> None:
        super().__init__(message)
        self.status = status
        self.code = code
        self.correlation_id = correlation_id


class SubmissionError(ValueError):
    """Treść żądania nie odpowiada podpisanemu zamiarowi stacji."""


class ResponseModel(BaseModel):
    """Baza modeli odpowiedzi: pozwalamy na dodatkowe pola backendu."""

    model_config = ConfigDict(extra="allow")


#: Typ modelu odpowiedzi używany w `SimulatorApiClient._parse`.
ResponseT = TypeVar("ResponseT", bound=ResponseModel)


class ProblemDetails(ResponseModel):
    """`application/problem+json`: stabilny kod domenowy i korelacja."""

    type: str | None = None
    title: str | None = None
    status: int | None = None
    code: str | None = None
    detail: str | None = None
    correlationId: str | None = None


class CommandAccepted(ResponseModel):
    """Odpowiedź `202`: polecenie przyjęte, jeszcze nie potwierdzone.

    Samo `202` nie jest zakończeniem etapu - status sprawdza `get_command`.
    """

    commandId: str
    status: str | None = None
    statusUrl: str | None = None


class CommandStatus(ResponseModel):
    """`200 CommandStatus`: stan polecenia, wynik i terminy wznowienia."""

    commandId: str
    status: CommandState
    outcome: Outcome | None = None
    retryAt: SafeInt | None = None
    retryAllowedAfter: SafeInt | None = None
    errorCode: str | None = None

    def is_terminal(self) -> bool:
        """Czy polecenie się zakończyło (`CONFIRMED`/`FAILED`)?"""
        return self.status in TERMINAL_COMMAND_STATES

    def is_confirmed(self) -> bool:
        """Czy potwierdzone na łańcuchu (`CONFIRMED`), także z `REJECTED`?"""
        return self.status == "CONFIRMED"

    def is_rejected(self) -> bool:
        """Czy raport został odrzucony (`qaPassed=false`), czyli partia bez zmiany?"""
        return self.status == "CONFIRMED" and self.outcome == "REJECTED"


class StageSubmission(StrictModel):
    """Treść `POST /api/v1/batches/{batchId}/stages`.

    Ścisła, bez nieznanych pól i bez niejawnych konwersji - wysyłamy dokładnie
    to, co zostało podpisane. Model buduj przez `build_stage_submission`, żeby
    `reportHash`, `deadline` i `signature` pochodziły z jednego podpisu.
    """

    report: Report
    stationCertificateId: Hex32
    reportHash: Hex32
    deadline: SafeInt
    signature: Signature65


def build_stage_submission(
    report: Report,
    certificate: StationCertificate,
    *,
    deadline: int,
    signed: SignedTypedData,
) -> StageSubmission:
    """Buduje zgłoszenie etapu i sprawdza, że odpowiada podpisanemu zamiarowi.

    Zamiar stacji to digest EIP-712 `StageCompletion` (`docs/protocol.md`,
    „Idempotencja”). Dlatego przed wysłaniem sprawdzamy: typ podpisu, adres
    podpisującego, zgodność certyfikatu z raportem, etap w dozwolonych etapach
    certyfikatu oraz każdą podpisaną wartość (`batchId`, `stageId`, `reportHash`,
    `qaPassed`, `deadline`). Rozbieżność to `SubmissionError` - nie wysyłamy
    treści, która nie jest tym, co podpisano.
    """
    if signed.primaryType != "StageCompletion":
        raise SubmissionError(
            f"zgłoszenie etapu wymaga podpisu StageCompletion, a otrzymano {signed.primaryType}"
        )

    if signed.signer.lower() != report.stationAddress:
        raise SubmissionError("podpis pochodzi z innego adresu niż stationAddress w raporcie")

    if certificate.payload.stationAddress != report.stationAddress:
        raise SubmissionError("certyfikat dotyczy innej stacji niż raport")

    if report.stageId not in certificate.payload.allowedStageIds:
        raise SubmissionError(
            f"etap {report.stageId} nie jest dozwolony w certyfikacie "
            f"({certificate.payload.allowedStageIds})"
        )

    expected = StageCompletionMessage(
        batchId=report.batchId,
        stageId=report.stageId,
        reportHash=report_hash(report),
        qaPassed=report.qaPassed,
        deadline=deadline,
    )
    signed_fields = message_payload(expected)
    for field, value in signed_fields.items():
        if signed.message.get(field) != value:
            raise SubmissionError(
                f"podpisany {field}={signed.message.get(field)!r} nie odpowiada "
                f"raportowi ({value!r})"
            )

    return StageSubmission(
        report=report,
        stationCertificateId=certificate.payload.certificateId,
        reportHash=expected.reportHash,
        deadline=deadline,
        signature=signed.signature,
    )


def _normalize_base_url(base_url: str) -> str:
    """Sprawdza adres backendu i usuwa końcowe ukośniki."""
    if not isinstance(base_url, str) or not base_url.startswith(("http://", "https://")):
        raise ValueError("adres backendu musi zaczynać się od http:// albo https://")
    normalized = base_url.rstrip("/")
    if normalized in {"http:", "https:"}:
        raise ValueError("adres backendu nie może być pusty")
    return normalized


class SimulatorApiClient:
    """Synchroniczny klient backendu dla jednego przebiegu symulacji.

    ``client`` i ``sleep`` służą do wstrzykiwania w testach (transport bez sieci
    i atrapa zegara). Klient wstrzyknięty nie jest zamykany przez ``close()`` -
    zamykamy tylko ten, który sami utworzyliśmy.

    Logi i komunikaty błędów nie zawierają tokenu, klucza prywatnego ani treści
    raportu; podajemy ścieżkę, status HTTP, kod domenowy i `correlationId`.
    """

    def __init__(
        self,
        base_url: str,
        *,
        token: str | None = None,
        timeout: float = DEFAULT_TIMEOUT_SECONDS,
        max_attempts: int = DEFAULT_MAX_ATTEMPTS,
        backoff_seconds: float = DEFAULT_BACKOFF_SECONDS,
        max_backoff_seconds: float = DEFAULT_MAX_BACKOFF_SECONDS,
        client: httpx.Client | None = None,
        sleep: Callable[[float], None] = time.sleep,
    ) -> None:
        self._base_url = _normalize_base_url(base_url)
        if token is not None and not token.strip():
            raise ValueError("token Bearer nie może być pusty; użyj None, gdy go nie ma")
        self._token = token
        if timeout <= 0:
            raise ValueError("timeout musi być dodatni")
        if max_attempts < 1:
            raise ValueError("max_attempts musi być co najmniej 1")
        if backoff_seconds < 0 or max_backoff_seconds < 0:
            raise ValueError("opóźnienia retry nie mogą być ujemne")
        self._timeout = timeout
        self._max_attempts = max_attempts
        self._backoff_seconds = backoff_seconds
        self._max_backoff_seconds = max_backoff_seconds
        self._sleep = sleep
        self._owns_client = client is None
        self._client = client if client is not None else httpx.Client(timeout=timeout)

    @property
    def base_url(self) -> str:
        """Adres backendu bez końcowego ukośnika."""
        return self._base_url

    def command_status_url(self, command_id: str) -> str:
        """Adres odczytu statusu polecenia (`GET /api/v1/commands/{commandId}`)."""
        return self._base_url + COMMANDS_PATH.format(command_id=command_id)

    def submit_stage(self, batch_id: str, submission: StageSubmission) -> CommandAccepted:
        """Wysyła podpisany raport etapu i zwraca przyjęte polecenie (`202`).

        Sprawdza spójność ścieżki z raportem, bo `batchId` w URL musi zgadzać
        się z raportem. Backend odpowiada `202` także dla `qaPassed=false`.
        """
        path_batch_id = batch_id.strip().lower() if isinstance(batch_id, str) else ""
        if not re.fullmatch(HEX_32_PATTERN, path_batch_id):
            raise ValueError("batchId w ścieżce musi być 0x + 64 znaki hex")
        if submission.report.batchId != path_batch_id:
            raise SubmissionError("batchId w ścieżce nie zgadza się z raportem")

        response = self._request(
            "POST",
            STAGES_PATH.format(batch_id=path_batch_id),
            body=submission.model_dump(mode="json"),
            expected_status=HTTP_ACCEPTED,
        )
        return self._parse(response, CommandAccepted, "202 CommandAccepted")

    def get_command(self, command_id: str) -> CommandStatus:
        """Odczytuje status polecenia (`GET /api/v1/commands/{commandId}`).

        Endpoint jest prywatny (właściciel albo Admin), więc potrzebny jest
        token Bearer. `404` oznacza nieznane polecenie.
        """
        text = command_id.strip() if isinstance(command_id, str) else ""
        if not text or "/" in text or any(character.isspace() for character in text):
            raise ValueError("commandId musi być niepustym identyfikatorem bez spacji i '/'")

        response = self._request(
            "GET",
            COMMANDS_PATH.format(command_id=text),
            expected_status=HTTP_OK,
        )
        return self._parse(response, CommandStatus, "200 CommandStatus")

    def close(self) -> None:
        """Zamyka klienta HTTP, jeśli należy do tego obiektu."""
        if self._owns_client:
            self._client.close()

    def __enter__(self) -> SimulatorApiClient:
        return self

    def __exit__(self, *exc_info: object) -> None:
        self.close()

    # --- wewnętrzne ---

    def _headers(self) -> dict[str, str]:
        """Nagłówki żądania: JSON, User-Agent i opcjonalny token Bearer."""
        headers = {
            "Accept": "application/json",
            "Content-Type": "application/json",
            "User-Agent": f"supply-chain-simulator/{__version__}",
        }
        if self._token is not None:
            headers["Authorization"] = f"Bearer {self._token}"
        return headers

    def _retry_delay(self, attempt: int, response: httpx.Response | None = None) -> float:
        """Opóźnienie przed kolejną próbą; `Retry-After` ma pierwszeństwo."""
        if response is not None:
            header = response.headers.get("Retry-After", "").strip()
            if header.isdigit():
                return min(float(header), self._max_backoff_seconds)
        return min(self._backoff_seconds * 2 ** (attempt - 1), self._max_backoff_seconds)

    def _request(
        self,
        method: str,
        path: str,
        *,
        body: dict[str, object] | None = None,
        expected_status: int,
    ) -> httpx.Response:
        """Wykonuje żądanie i ponawia je identycznie po awarii przejściowej.

        Te same bajty w każdej próbie: podpis jest tożsamością zamiaru stacji
        (brak nonce), więc retry nie może zmienić raportu ani deadline'u.
        """
        attempt = 0
        while True:
            attempt += 1
            try:
                response = self._client.request(
                    method,
                    self._base_url + path,
                    json=body,
                    headers=self._headers(),
                )
            except httpx.TimeoutException:
                failure = f"timeout {self._timeout:g}s na {path}"
            except httpx.TransportError as exc:
                failure = f"błąd transportu na {path}: {exc}"
            else:
                if response.status_code == expected_status:
                    return response
                if response.status_code in RETRY_STATUS_CODES and attempt < self._max_attempts:
                    self._sleep(self._retry_delay(attempt, response))
                    continue
                raise self._api_error(response, path)

            if attempt >= self._max_attempts:
                raise ApiError(
                    f"{failure}; odczytaj status polecenia albo ponów to samo żądanie "
                    "(identyczna treść i podpis, bez nowego raportu)",
                )
            self._sleep(self._retry_delay(attempt))

    def _problem(self, response: httpx.Response) -> ProblemDetails:
        """Odczytuje `application/problem+json`; brak treści to pusty model."""
        content_type = response.headers.get("Content-Type", "").lower()
        if "json" not in content_type:
            return ProblemDetails()
        try:
            return ProblemDetails(**response.json())
        except (TypeError, ValueError, ValidationError):
            return ProblemDetails()

    def _api_error(self, response: httpx.Response, path: str) -> ApiError:
        """Zamienia odpowiedź błędu na `ApiError` z kodem i korelacją."""
        problem = self._problem(response)
        parts = [f"backend zwrócił {response.status_code} na {path}"]
        if problem.code:
            parts.append(problem.code)
        if problem.detail:
            parts.append(str(problem.detail))
        if problem.correlationId:
            parts.append(f"correlationId={problem.correlationId}")
        if response.status_code == 409 and problem.code == "STAGE_ALREADY_PENDING":
            parts.append(
                "dla tego (batchId, stageId) jest aktywne polecenie: powtórz identyczne "
                "żądanie albo odczytaj status, nie twórz nowego raportu"
            )
        if response.status_code == 429:
            parts.append("limit żądań: ponów po terminie z retryAllowedAfter")
        if response.status_code == 503:
            parts.append("backend nie przyjął polecenia trwale; bez nowego raportu")
        return ApiError(
            "; ".join(parts),
            status=response.status_code,
            code=problem.code,
            correlation_id=problem.correlationId,
        )

    def _parse(
        self,
        response: httpx.Response,
        model: type[ResponseT],
        label: str,
    ) -> ResponseT:
        """Waliduje odpowiedź modelem; niezgodność to `ApiError` (fail-closed)."""
        try:
            return model(**response.json())
        except (TypeError, ValueError, ValidationError) as exc:
            raise ApiError(
                f"odpowiedź {label} niezgodna z modelem: {exc}",
                status=response.status_code,
            ) from exc
