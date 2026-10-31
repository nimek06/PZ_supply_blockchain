"""Budowa wspólnego wektora podpisu `shared/fixtures/signature-vector.json` (T008).

Wektor jest deterministyczny: te same dane wejściowe dają ten sam plik.
Klucze to publiczne, znane klucze deweloperskie Anvil (konta #1 i #2) - nie ma
ich w pliku JSON i nigdy nie wolno zasilać tych adresów na Amoy.
Wartości pomiarów są danymi testowymi, nie progami (DEC-02 jest otwarte).
"""

from __future__ import annotations

import json
from pathlib import Path

from eth_utils import keccak

from supply_chain_simulator.canonical import canonical_text, report_hash
from supply_chain_simulator.models import (
    Eip712Domain,
    Report,
    StageApprovalMessage,
    StageCompletionMessage,
    report_payload,
)
from supply_chain_simulator.signing import (
    EIP712_DOMAIN_TYPE_STRING,
    SignedTypedData,
    sign_stage_approval,
    sign_stage_completion,
)

# Publiczne klucze deweloperskie Anvil - te same co w contracts/test/SignatureVector.t.sol.
ANVIL_KEY_1 = 0x59C6995E998F97A5A0044966F0945389DC9E86DAE88C7A8412F4603B6B78690D
ANVIL_KEY_2 = 0x5DE4111AFA1A4B94908F83103EB1F1706367C2E68CA870FC3FB9A804CDAB365A
ANVIL_ADDRESS_1 = "0x70997970C51812dc3A010C7d01b50e0d17dc79C8"
ANVIL_ADDRESS_2 = "0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC"

VECTOR_CONTRACT = "0x5FbDB2315678afecb367f032d93F642f64180aa3"
VECTOR_CHAIN_ID = 31337
VECTOR_BATCH_NUMBER = "B-2026-0001"
VECTOR_STAGE_ID = 2
VECTOR_TIMESTAMP = 1_800_000_000
VECTOR_DEADLINE = 1_800_000_600
VECTOR_MEASUREMENTS: dict[str, int] = {
    "temperatureMilliC": 21_500,
    "torqueMilliNm": 45_000,
    "pressurePa": 620_000,
}

VECTOR_VERSION = 1
VECTOR_DESCRIPTION = (
    "Wspólny wektor podpisu v1 (T008): raport JCS, reportHash oraz podpisy EIP-712 "
    "StageCompletion (stacja) i StageApproval (operator zmiany). Klucze to publiczne "
    "klucze deweloperskie Anvil #1 i #2 - nie zasilaj tych adresów na Amoy. "
    "Wartości pomiarów są testowe, nie są progami (DEC-02 otwarte)."
)


def batch_id(batch_number: str) -> str:
    """`batchId = keccak256(utf8(batchNumber))` (V2-11, DEC-12)."""
    return "0x" + bytes(keccak(batch_number.encode("utf-8"))).hex()


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
