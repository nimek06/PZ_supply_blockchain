"""Budowa wspólnych wektorów w `shared/fixtures/` (T008).

Dwa pliki, oba deterministyczne (te same dane wejściowe dają identyczny plik):

- `signature-vector.json` - raport JCS, `reportHash` i podpisy EIP-712
  `StageCompletion` (stacja) oraz `StageApproval` (operator zmiany);
- `station-certificate.json` - przykładowy certyfikat stacji z podpisem
  wystawcy (`StationCertificate`, DEC-13; format nadal propozycją).

Klucze to publiczne, znane klucze deweloperskie Anvil (konta #0-#2) - nie ma
ich w plikach JSON i nigdy nie wolno zasilać tych adresów na Amoy.
Wartości pomiarów są demonstracyjne (profil e-bike, DEC-02), nie normami.
"""

from __future__ import annotations

import json
from pathlib import Path

from supply_chain_simulator.canonical import canonical_text, keccak256_text, report_hash
from supply_chain_simulator.certificate import (
    certificate_domain,
    certificate_id,
    certificate_payload_text,
    sign_certificate,
    stage_mask,
    station_id,
)
from supply_chain_simulator.models import (
    CertificatePayload,
    Eip712Domain,
    Report,
    StageApprovalMessage,
    StageCompletionMessage,
    StationCertificate,
    report_payload,
)
from supply_chain_simulator.signing import (
    EIP712_DOMAIN_TYPE_STRING,
    SignedTypedData,
    sign_stage_approval,
    sign_stage_completion,
)

# Publiczne klucze deweloperskie Anvil - te same co w contracts/test/SignatureVector.t.sol.
ANVIL_KEY_0 = 0xAC0974BEC39A17E36BA4A6B4D238FF944BACB478CBED5EFCAE784D7BF4F2FF80
ANVIL_KEY_1 = 0x59C6995E998F97A5A0044966F0945389DC9E86DAE88C7A8412F4603B6B78690D
ANVIL_KEY_2 = 0x5DE4111AFA1A4B94908F83103EB1F1706367C2E68CA870FC3FB9A804CDAB365A
ANVIL_ADDRESS_0 = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
ANVIL_ADDRESS_1 = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8"
ANVIL_ADDRESS_2 = "0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC"

VECTOR_CONTRACT = "0x5FbDB2315678afecb367f032d93F642f64180aa3"
VECTOR_CHAIN_ID = 31337
VECTOR_BATCH_NUMBER = "B-2026-0001"
VECTOR_STAGE_ID = 2
VECTOR_TIMESTAMP = 1_800_000_000
VECTOR_DEADLINE = 1_800_000_600
# Etap kontroli e-bike wg profilu profileQualityControlV1 (shared/schemas/telemetry.schema.json):
# 10 sprawdzonych rowerów z partii, brak wad, 12 z 12 punktów listy kontrolnej.
VECTOR_MEASUREMENTS: dict[str, int] = {
    "inspectedUnits": 10,
    "defectsFound": 0,
    "checklistItemsTotal": 12,
    "checklistItemsPassed": 12,
}

VECTOR_VERSION = 1
VECTOR_DESCRIPTION = (
    "Wspólny wektor podpisu v1 (T008): raport JCS, reportHash oraz podpisy EIP-712 "
    "StageCompletion (stacja) i StageApproval (operator zmiany). Scenariusz: kontrola "
    "jakości roweru elektrycznego (etap 2, profileQualityControlV1, DEC-02). Klucze to "
    "publiczne klucze deweloperskie Anvil #1 i #2 - nie zasilaj tych adresów na Amoy. "
    "Wartości pomiarów są demonstracyjne, nie normy."
)

CERTIFICATE_FIXTURE_VERSION = 1
CERTIFICATE_STATION_REFERENCE = "ST-001"
CERTIFICATE_SERIAL = "CERT-2026-0001"
CERTIFICATE_ISSUED_AT = VECTOR_TIMESTAMP
CERTIFICATE_VALIDITY_SECONDS = 365 * 24 * 3600
CERTIFICATE_EXPIRES_AT = CERTIFICATE_ISSUED_AT + CERTIFICATE_VALIDITY_SECONDS
CERTIFICATE_ALLOWED_STAGE_IDS: list[int] = [0, 1, 2, 3]
CERTIFICATE_DESCRIPTION = (
    "Przykładowy certyfikat stacji (DEC-13): payload + podpis EIP-712 wystawcy "
    "w osobnej domenie SupplyChainStationRegistry i typie StationCertificate. "
    "Wystawcą jest publiczny klucz deweloperski Anvil #0 (lokalny admin, "
    "DEFAULT_ADMIN_ROLE) - nie zasilaj tego adresu na Amoy. stationId = "
    "keccak256(\"ST-001\"), expiresAt = issuedAt + 365 dni i stageMask 0x0F "
    "odpowiadają rejestracji w contracts/test/SupplyChain.t.sol. Podpis "
    "certyfikatu nie jest podpisem StageCompletion i nie zastępuje rejestracji "
    "on-chain. Typ i nazwa domeny są nadal propozycją DEC-13."
)


def batch_id(batch_number: str) -> str:
    """`batchId = keccak256(utf8(batchNumber))` (V2-11, DEC-12)."""
    return keccak256_text(batch_number)


def vector_domain() -> Eip712Domain:
    """Domena wektora: `SupplyChain`/`1`, chainId 31337, stały adres kontraktu."""
    return Eip712Domain(
        name="SupplyChain",
        version="1",
        chainId=VECTOR_CHAIN_ID,
        verifyingContract=VECTOR_CONTRACT,
    )


def vector_report() -> Report:
    """Raport testowy etapu kontroli (stageId 2) podpisany kluczem Anvil #1."""
    return Report(
        schemaVersion=1,
        batchId=batch_id(VECTOR_BATCH_NUMBER),
        stageId=VECTOR_STAGE_ID,
        stationAddress=ANVIL_ADDRESS_1,
        timestamp=VECTOR_TIMESTAMP,
        measurements=dict(VECTOR_MEASUREMENTS),
        qaPassed=True,
    )


def _stage_block(signed: SignedTypedData) -> dict[str, object]:
    block = signed.as_dict()
    return {
        "typeString": block["typeString"],
        "types": block["types"],
        "message": block["message"],
        "digest": block["digest"],
        "signature": block["signature"],
        "signer": block["signer"],
    }


def build_signature_vector() -> dict[str, object]:
    """Buduje treść wektora (dict gotowy do zapisu jako JSON)."""
    domain = vector_domain()
    report = vector_report()
    payload = report_payload(report)

    station = signed_station_completion(domain, report)
    operator = signed_stage_approval(domain, report)

    return {
        "vectorVersion": VECTOR_VERSION,
        "description": VECTOR_DESCRIPTION,
        "domainTypeString": EIP712_DOMAIN_TYPE_STRING,
        "domain": domain.model_dump(mode="json"),
        "batchNumber": VECTOR_BATCH_NUMBER,
        "report": payload,
        "canonicalReportJcs": canonical_text(payload),
        "reportHash": report_hash(report),
        "stageCompletion": _stage_block(station),
        "stageApproval": _stage_block(operator),
    }


def signed_station_completion(
    domain: Eip712Domain, report: Report
) -> SignedTypedData:
    """Podpis stacji dla raportu (stacja = raportujący)."""
    message = StageCompletionMessage(
        batchId=report.batchId,
        stageId=report.stageId,
        reportHash=report_hash(report),
        qaPassed=report.qaPassed,
        deadline=VECTOR_DEADLINE,
    )
    return sign_stage_completion(domain, message, ANVIL_KEY_1)


def signed_stage_approval(domain: Eip712Domain, report: Report) -> SignedTypedData:
    """Podpis operatora zmiany (Anvil #2) dla tego samego hasha."""
    message = StageApprovalMessage(
        batchId=report.batchId,
        stageId=report.stageId,
        reportHash=report_hash(report),
        deadline=VECTOR_DEADLINE,
    )
    return sign_stage_approval(domain, message, ANVIL_KEY_2)


def signature_vector_text() -> str:
    """Serializowany wektor JSON (2 spacje wcięcia, końcowy znak nowej linii)."""
    return json.dumps(build_signature_vector(), indent=2, ensure_ascii=False) + "\n"


def default_vector_path() -> Path:
    """Ścieżka `shared/fixtures/signature-vector.json` w tym repozytorium."""
    return Path(__file__).resolve().parents[3] / "shared" / "fixtures" / "signature-vector.json"


def write_signature_vector(path: Path | None = None) -> Path:
    """Zapisuje wektor i zwraca ścieżkę pliku."""
    target = path if path is not None else default_vector_path()
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(signature_vector_text(), encoding="utf-8")
    return target


def certificate_payload() -> CertificatePayload:
    """Payload certyfikatu testowego dla stacji `ST-001` (Anvil #1, wszystkie etapy)."""
    return CertificatePayload(
        schemaVersion=1,
        certificateId=certificate_id(CERTIFICATE_SERIAL),
        stationId=station_id(CERTIFICATE_STATION_REFERENCE),
        stationAddress=ANVIL_ADDRESS_1,
        issuerAddress=ANVIL_ADDRESS_0,
        chainId=VECTOR_CHAIN_ID,
        verifyingContract=VECTOR_CONTRACT,
        issuedAt=CERTIFICATE_ISSUED_AT,
        expiresAt=CERTIFICATE_EXPIRES_AT,
        allowedStageIds=list(CERTIFICATE_ALLOWED_STAGE_IDS),
    )


def build_certificate_fixture() -> dict[str, object]:
    """Buduje treść fixture'u certyfikatu (dict gotowy do zapisu jako JSON).

    Obwiednia `certificate` jest dokumentem zgodnym ze schematem
    `shared/schemas/station-certificate.schema.json`. Blok
    `certificateSignature` powtarza format wektora podpisu, żeby Java, TS
    i Solidity mogły zweryfikować podpis bez czytania modeli Pythona, a
    `registryCall` podaje argumenty `registerStation` dla admina (Adam, T012).
    """
    payload = certificate_payload()
    certificate, signed = sign_certificate(payload, ANVIL_KEY_0)
    block = signed.as_dict()

    return {
        "fixtureVersion": CERTIFICATE_FIXTURE_VERSION,
        "description": CERTIFICATE_DESCRIPTION,
        "domainTypeString": EIP712_DOMAIN_TYPE_STRING,
        "domain": certificate_domain(payload).model_dump(mode="json"),
        "certificate": certificate.model_dump(mode="json"),
        "canonicalPayloadJcs": certificate_payload_text(payload),
        "certificateSignature": {
            "typeString": block["typeString"],
            "types": block["types"],
            "message": block["message"],
            "digest": block["digest"],
            "signature": block["signature"],
            "signer": block["signer"],
        },
        "registryCall": {
            "station": payload.stationAddress,
            "stationId": payload.stationId,
            "expiresAt": payload.expiresAt,
            "stageMask": stage_mask(payload.allowedStageIds),
        },
    }


def certificate_fixture_text() -> str:
    """Serializowany fixture certyfikatu (2 spacje wcięcia, końcowa nowa linia)."""
    return json.dumps(build_certificate_fixture(), indent=2, ensure_ascii=False) + "\n"


def default_certificate_fixture_path() -> Path:
    """Ścieżka `shared/fixtures/station-certificate.json` w tym repozytorium."""
    return (
        Path(__file__).resolve().parents[3]
        / "shared"
        / "fixtures"
        / "station-certificate.json"
    )


def write_certificate_fixture(path: Path | None = None) -> Path:
    """Zapisuje fixture certyfikatu i zwraca ścieżkę pliku."""
    target = path if path is not None else default_certificate_fixture_path()
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(certificate_fixture_text(), encoding="utf-8")
    return target


def load_certificate_fixture(path: Path | None = None) -> StationCertificate:
    """Wczytuje obwiednię certyfikatu z fixture'u (pole `certificate`).

    Fixture zawiera metadane weryfikacyjne obok dokumentu, dlatego certyfikat
    siedzi w `certificate`; plik wystawiony przez CLI jest już samą obwiednią
    i czyta go `identity.load_certificate`.
    """
    source = path if path is not None else default_certificate_fixture_path()
    document = json.loads(source.read_text(encoding="utf-8"))
    return StationCertificate(**document["certificate"])
