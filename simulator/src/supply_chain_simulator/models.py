"""Ścisłe modele danych symulatora: raport v1 i struktury EIP-712.

Nazwy pól raportu pochodzą z `docs/protocol.md` (sekcja „Raport i typy”):
`schemaVersion`, `batchId`, `stageId`, `stationAddress`, `timestamp`,
`measurements`, `qaPassed`. Żadne pole obwiedni (`deadline`,
`approvalDeadline`, `signature`, `ipfsCid`) nie należy do raportu objętego JCS
i nie wolno go do niego dopisywać.

Modele odrzucają nieznane pola (`extra="forbid"`) i niejawne konwersje
(`strict=True`), np. `"true"` zamiast `true` albo `1.0` zamiast `1`.
Zakresy pomiarów (progi) nie są tu ustalane - zależą od DEC-02.

Certyfikat stacji (DEC-13) ma osobne modele: `CertificatePayload` (pola z
`docs/station-identity.md`) i obwiednię `StationCertificate` z podpisem wystawcy.
Podpis certyfikatu liczy `certificate.py` - to inny typ EIP-712 niż
`StageCompletion` i `StageApproval`.
"""

from __future__ import annotations

import re
from typing import Annotated, Any, Literal

from pydantic import (
    BaseModel,
    ConfigDict,
    Field,
    StringConstraints,
    field_validator,
    model_validator,
)

SCHEMA_VERSION = 1
#: Wersja payloadu certyfikatu stacji (`payload.schemaVersion`, DEC-13).
CERTIFICATE_SCHEMA_VERSION = 1
#: Wersja obwiedni certyfikatu: `payload` + podpis wystawcy (DEC-13).
CERTIFICATE_ENVELOPE_VERSION = 1
STAGE_MIN = 0
STAGE_MAX = 3
#: Granica bezpiecznych liczb całkowitych w JSON (Number.MAX_SAFE_INTEGER).
MAX_SAFE_INTEGER = 9_007_199_254_740_991

HEX_32_PATTERN = r"^0x[0-9a-f]{64}$"
ADDRESS_PATTERN = r"^0x[0-9a-fA-F]{40}$"
SIGNATURE_PATTERN = r"^0x[0-9a-f]{130}$"
MEASUREMENT_KEY_PATTERN = r"^[a-z][A-Za-z0-9]*$"

Hex32 = Annotated[str, StringConstraints(pattern=HEX_32_PATTERN)]
StageId = Annotated[int, Field(ge=STAGE_MIN, le=STAGE_MAX)]
SafeInt = Annotated[int, Field(ge=0, le=MAX_SAFE_INTEGER)]
ChainId = Annotated[int, Field(ge=1, le=MAX_SAFE_INTEGER)]
Signature65 = Annotated[str, StringConstraints(pattern=SIGNATURE_PATTERN)]


class StrictModel(BaseModel):
    """Baza: brak nieznanych pól, brak niejawnych konwersji."""

    model_config = ConfigDict(extra="forbid", strict=True)


def normalize_address(value: str) -> str:
    """Zwraca adres EVM małymi literami (forma kanoniczna w raporcie)."""
    if not isinstance(value, str) or not re.fullmatch(ADDRESS_PATTERN, value):
        raise ValueError("adres musi mieć postać 0x + 40 znaków hex")
    return value.lower()


class Eip712Domain(StrictModel):
    """Domena EIP-712. `verifyingContract` zawsze małymi literami."""

    name: str
    version: str
    chainId: SafeInt
    verifyingContract: str

    @field_validator("verifyingContract")
    @classmethod
    def _normalize(cls, value: str) -> str:
        return normalize_address(value)


class Report(StrictModel):
    """Raport v1 - dokładnie ta treść jest kanonizowana i podpisywana."""

    schemaVersion: Literal[1]
    batchId: Hex32
    stageId: StageId
    stationAddress: str
    timestamp: SafeInt
    measurements: dict[str, SafeInt]
    qaPassed: bool

    @field_validator("stationAddress")
    @classmethod
    def _normalize(cls, value: str) -> str:
        return normalize_address(value)

    @field_validator("measurements")
    @classmethod
    def _validate_measurements(cls, value: dict[str, int]) -> dict[str, int]:
        if not value:
            raise ValueError("measurements nie może być pusty")
        for key in value:
            if not re.fullmatch(MEASUREMENT_KEY_PATTERN, key):
                raise ValueError(f"niedozwolony klucz pomiaru: {key!r}")
        return value


class StageCompletionMessage(StrictModel):
    """`StageCompletion(bytes32,uint32,bytes32,bool,uint256)` - podpis stacji, bez nonce."""

    batchId: Hex32
    stageId: StageId
    reportHash: Hex32
    qaPassed: bool
    deadline: SafeInt


class StageApprovalMessage(StrictModel):
    """`StageApproval(bytes32,uint32,bytes32,uint256)` - podpis operatora zmiany."""

    batchId: Hex32
    stageId: StageId
    reportHash: Hex32
    deadline: SafeInt


class StationCertificateMessage(StrictModel):
    """`StationCertificate(bytes32 payloadHash)` - podpis wystawcy certyfikatu (DEC-13).

    Obowiązuje tylko dla certyfikatu: podpisu tego typu nie wolno użyć jako
    `StageCompletion` ani odwrotnie (inne type hashes, `docs/protocol.md`).
    """

    payloadHash: Hex32


#: Wiadomości EIP-712 obsługiwane przez `signing.py`.
SignedMessage = StageCompletionMessage | StageApprovalMessage | StationCertificateMessage


class CertificatePayload(StrictModel):
    """Payload certyfikatu stacji - dokładnie te pola są kanonizowane i podpisywane.

    Nazwy i kolejność pól pochodzą z `docs/station-identity.md` (propozycja
    DEC-13). Adresy i identyfikatory zapisujemy małymi literami; `allowedStageIds`
    musi odpowiadać `stageMask` w rejestrze on-chain, a zgodność z rejestrem
    sprawdza backend, nie ten model. Danych organizacyjnych, daty odbioru
    w backendzie i klucza prywatnego nie ma w payloadzie.
    """

    schemaVersion: Literal[1]
    certificateId: Hex32
    stationId: Hex32
    stationAddress: str
    issuerAddress: str
    chainId: ChainId
    verifyingContract: str
    issuedAt: SafeInt
    expiresAt: SafeInt
    allowedStageIds: list[StageId]

    @field_validator("stationAddress", "issuerAddress", "verifyingContract")
    @classmethod
    def _normalize_addresses(cls, value: str) -> str:
        return normalize_address(value)

    @field_validator("allowedStageIds")
    @classmethod
    def _validate_stage_ids(cls, value: list[int]) -> list[int]:
        if not value:
            raise ValueError("allowedStageIds nie może być puste")
        if value != sorted(set(value)):
            raise ValueError("allowedStageIds musi być rosnącą listą unikalnych etapów 0..3")
        return value

    @model_validator(mode="after")
    def _validate_validity_window(self) -> CertificatePayload:
        if self.expiresAt <= self.issuedAt:
            raise ValueError("expiresAt musi być większe od issuedAt")
        return self


class StationCertificate(StrictModel):
    """Obwiednia certyfikatu: `payload` i 65-bajtowy podpis wystawcy.

    Podpis nie jest częścią hashowanego payloadu - hash obejmuje wyłącznie
    `payload`, tak samo w Pythonie i w Javie.
    """

    certificateVersion: Literal[1]
    payload: CertificatePayload
    payloadHash: Hex32
    signature: Signature65


def report_payload(report: Report) -> dict[str, Any]:
    """Zwraca dict raportu gotowy do kanonizacji (bez pól `None`)."""
    return report.model_dump(mode="json")


def message_payload(message: SignedMessage) -> dict[str, Any]:
    """Zwraca dict podpisanej struktury gotowy do EIP-712."""
    return message.model_dump(mode="json")


def certificate_payload_dict(payload: CertificatePayload) -> dict[str, Any]:
    """Zwraca dict payloadu certyfikatu gotowy do kanonizacji JCS."""
    return payload.model_dump(mode="json")
