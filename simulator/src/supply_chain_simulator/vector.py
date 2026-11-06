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

# --- Wektory negatywne (tydzień 2) ------------------------------------------
# Rodzina fixture'ów dla walidatora profilu (osoba 2) i scenariuszy (osoba 4):
# - report-profile-violation-stageN.json (N=0..3): raport z `qaPassed=true`, który
#   łamie regułę `qaPassed` swojego etapu -> backend ma zwrócić `422` PROFILE_VIOLATION;
# - report-qa-fail.json: etap 1, `qaPassed=false` spójne z regułą -> `202` z
#   `outcome=REJECTED` (kontrola, że walidator nie odrzuca zgodnego wyniku negatywnego).
# Format JSON Schema i podpis StageCompletion (Anvil #1) są poprawne, żeby odrzucenie
# wynikało z reguły profilu, a nie z formatu (400) czy podpisu (403). Każdy plik ma
# własną partię, żeby testy się nie kolidowały. Klucze to publiczne klucze Anvil #1.
NEGATIVE_FIXTURE_VECTOR_VERSION = 1
PROFILE_VIOLATION_STAGES: tuple[int, ...] = (0, 1, 2, 3)
# Nazwa profilu per stage (shared/schemas/telemetry.schema.json) do `expectedViolation`.
PROFILE_VIOLATION_PROFILES: dict[int, str] = {
    0: "profilePreparationV1",
    1: "profileAssemblyV1",
    2: "profileQualityControlV1",
    3: "profilePackagingV1",
}
# Partie: etap 2 zachowuje B-2026-0002 (plik opublikowany wcześniej), reszta obok.
PROFILE_VIOLATION_BATCH_NUMBERS: dict[int, str] = {
    0: "B-2026-0003",
    1: "B-2026-0004",
    2: "B-2026-0002",
    3: "B-2026-0005",
}
# Pomiary łamiące regułę `qaPassed` swojego etapu (przy `qaPassed=true`).
PROFILE_VIOLATION_MEASUREMENTS: dict[int, dict[str, int]] = {
    0: {"checklistItemsTotal": 12, "checklistItemsPassed": 11},
    1: {"operationsTotal": 8, "operationsCompleted": 7, "cycleTimeMs": 420_000},
    2: {
        "inspectedUnits": 10,
        "defectsFound": 1,
        "checklistItemsTotal": 12,
        "checklistItemsPassed": 12,
    },
    3: {"unitsPacked": 10, "labelsApplied": 9},
}
PROFILE_VIOLATION_REASONS: dict[int, str] = {
    0: "qaPassed=true wymaga checklistItemsPassed==checklistItemsTotal; jest 11/12",
    1: "qaPassed=true wymaga operationsCompleted==operationsTotal; jest 7/8",
    2: "qaPassed=true wymaga defectsFound==0 i checklistItemsPassed==checklistItemsTotal; defectsFound=1",
    3: "qaPassed=true wymaga labelsApplied==unitsPacked; jest 9/10",
}
# Zgodny wynik negatywny (etap 1): poprawny raport, outcome=REJECTED, nie 422.
QA_FAIL_BATCH_NUMBER = "B-2026-0006"
QA_FAIL_MEASUREMENTS: dict[str, int] = {
    "operationsTotal": 8,
    "operationsCompleted": 7,
    "cycleTimeMs": 420_000,
}

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


def _negative_report(
    stage_id: int, batch_number: str, measurements: dict[str, int], qa_passed: bool
) -> Report:
    """Raport etapu z podanymi pomiarami i wynikiem `qaPassed` (wspólny mianownik)."""
    return Report(
        schemaVersion=1,
        batchId=batch_id(batch_number),
        stageId=stage_id,
        stationAddress=ANVIL_ADDRESS_1,
        timestamp=VECTOR_TIMESTAMP,
        measurements=dict(measurements),
        qaPassed=qa_passed,
    )


def _negative_fixture(
    batch_number: str, report: Report, description: str, tail: dict[str, object]
) -> dict[str, object]:
    """Buduje fixture negatywny: raport, podpis stacji, blok `submission` i `tail`."""
    domain = vector_domain()
    payload = report_payload(report)
    block = _stage_block(signed_station_completion(domain, report))
    report_hash_value = report_hash(report)

    fixture: dict[str, object] = {
        "vectorVersion": NEGATIVE_FIXTURE_VECTOR_VERSION,
        "description": description,
        "domainTypeString": EIP712_DOMAIN_TYPE_STRING,
        "domain": domain.model_dump(mode="json"),
        "batchNumber": batch_number,
        "report": payload,
        "canonicalReportJcs": canonical_text(payload),
        "reportHash": report_hash_value,
        "stageCompletion": block,
        "submission": {
            "stationCertificateId": certificate_id(CERTIFICATE_SERIAL),
            "reportHash": report_hash_value,
            "deadline": VECTOR_DEADLINE,
            "signature": block["signature"],
        },
    }
    fixture.update(tail)
    return fixture


def _profile_violation_description(stage_id: int) -> str:
    return (
        f"Wektor negatywny v1 (tydzień 2): raport etapu {stage_id} "
        f"({PROFILE_VIOLATION_PROFILES[stage_id]}, DEC-02) niezgodny z regułą `qaPassed` "
        f"- {PROFILE_VIOLATION_REASONS[stage_id]}. Format JSON Schema jest poprawny, a podpis "
        "EIP-712 stacji (Anvil #1) jest ważny, więc backend musi odrzucić raport przez "
        "walidację profilu: `422` z kodem `PROFILE_VIOLATION`. Blok `submission` to gotowe "
        "ciało `POST /api/v1/batches/{batchId}/stages`. Klucze to publiczne klucze "
        "deweloperskie Anvil #1 - nie zasilaj tego adresu na Amoy."
    )


QA_FAIL_DESCRIPTION = (
    "Wektor negatywny v1 (tydzień 2): etap 1 (profileAssemblyV1, DEC-02) z `qaPassed=false` "
    "ZGODNYM z regułą (operationsCompleted 7 < operationsTotal 8) - to nie naruszenie profilu, "
    "lecz poprawny raport z wynikiem negatywnym. Backend ma zwrócić `202` i zakończyć polecenie "
    "`outcome=REJECTED` po `StageRejected` (docs/corrections.md), a nie `422`. Kontrola, że "
    "walidator profilu nie odrzuca zgodnego wyniku negatywnego. Blok `submission` to gotowe "
    "ciało `POST /api/v1/batches/{batchId}/stages`. Klucze to publiczne klucze deweloperskie "
    "Anvil #1 - nie zasilaj tego adresu na Amoy."
)


def profile_violation_report(stage_id: int) -> Report:
    """Raport etapu `stage_id` łamiący regułę `qaPassed` (przy `qaPassed=true`)."""
    return _negative_report(
        stage_id,
        PROFILE_VIOLATION_BATCH_NUMBERS[stage_id],
        PROFILE_VIOLATION_MEASUREMENTS[stage_id],
        qa_passed=True,
    )


def build_profile_violation_fixture(stage_id: int) -> dict[str, object]:
    """Buduje treść negatywnego wektora dla etapu `stage_id` (dict do zapisu jako JSON)."""
    tail: dict[str, object] = {
        "expectedViolation": {
            "profile": PROFILE_VIOLATION_PROFILES[stage_id],
            "code": "PROFILE_VIOLATION",
            "status": 422,
            "reason": PROFILE_VIOLATION_REASONS[stage_id],
        }
    }
    return _negative_fixture(
        PROFILE_VIOLATION_BATCH_NUMBERS[stage_id],
        profile_violation_report(stage_id),
        _profile_violation_description(stage_id),
        tail,
    )


def profile_violation_fixture_name(stage_id: int) -> str:
    """Nazwa pliku negatywnego wektora etapu `stage_id`."""
    return f"report-profile-violation-stage{stage_id}.json"


def profile_violation_fixture_text(stage_id: int) -> str:
    """Serializowany negatywny wektor etapu `stage_id` (2 spacje, końcowa nowa linia)."""
    return (
        json.dumps(build_profile_violation_fixture(stage_id), indent=2, ensure_ascii=False)
        + "\n"
    )


def qa_fail_report() -> Report:
    """Raport etapu 1 z `qaPassed=false` zgodnym z regułą (poprawny wynik negatywny)."""
    return _negative_report(1, QA_FAIL_BATCH_NUMBER, QA_FAIL_MEASUREMENTS, qa_passed=False)


def build_qa_fail_fixture() -> dict[str, object]:
    """Buduje fixture `report-qa-fail.json` (poprawny raport, outcome=REJECTED)."""
    tail: dict[str, object] = {
        "expectedOutcome": {"status": 202, "outcome": "REJECTED", "event": "StageRejected"}
    }
    return _negative_fixture(QA_FAIL_BATCH_NUMBER, qa_fail_report(), QA_FAIL_DESCRIPTION, tail)


QA_FAIL_FIXTURE_NAME = "report-qa-fail.json"


def qa_fail_fixture_text() -> str:
    """Serializowany fixture `report-qa-fail.json`."""
    return json.dumps(build_qa_fail_fixture(), indent=2, ensure_ascii=False) + "\n"


def default_negative_fixture_directory() -> Path:
    """Katalog `shared/fixtures/` w tym repozytorium."""
    return Path(__file__).resolve().parents[3] / "shared" / "fixtures"


def negative_fixture_names() -> list[str]:
    """Nazwy wszystkich plików negatywnych (4 naruszenia profilu + zgodny negatyw)."""
    return [
        *(profile_violation_fixture_name(stage_id) for stage_id in PROFILE_VIOLATION_STAGES),
        QA_FAIL_FIXTURE_NAME,
    ]


def write_negative_fixtures(directory: Path | None = None) -> list[Path]:
    """Zapisuje wszystkie negatywne fixture'y do katalogu i zwraca ścieżki plików."""
    target = directory if directory is not None else default_negative_fixture_directory()
    target.mkdir(parents=True, exist_ok=True)
    written: list[Path] = []
    for stage_id in PROFILE_VIOLATION_STAGES:
        path = target / profile_violation_fixture_name(stage_id)
        path.write_text(profile_violation_fixture_text(stage_id), encoding="utf-8")
        written.append(path)
    qa_fail_path = target / QA_FAIL_FIXTURE_NAME
    qa_fail_path.write_text(qa_fail_fixture_text(), encoding="utf-8")
    written.append(qa_fail_path)
    return written


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
