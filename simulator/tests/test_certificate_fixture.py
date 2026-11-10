"""Testy wspólnego fixture'u certyfikatu stacji i schematu (DEC-13).

Plik `shared/fixtures/station-certificate.json` mają czytać Java, TS i testy
kontraktu, dlatego testy sprawdzają odtwarzalność pliku, zgodność obwiedni
`certificate` ze schematem `shared/schemas/station-certificate.schema.json`,
spójność z wektorem podpisu oraz brak kluczy prywatnych.
"""

from __future__ import annotations

import copy
import json
from pathlib import Path

import pytest
from supply_chain_simulator.canonical import keccak256_text
from supply_chain_simulator.certificate import (
    certificate_domain,
    certificate_message,
    certificate_payload_hash,
    certificate_payload_text,
    recovered_issuer,
    stage_mask,
    verify_certificate,
)
from supply_chain_simulator.cli import EXIT_OK, main
from supply_chain_simulator.models import CertificatePayload, StationCertificate
from supply_chain_simulator.signing import digest
from supply_chain_simulator.vector import (
    ANVIL_ADDRESS_0,
    ANVIL_ADDRESS_1,
    ANVIL_ADDRESS_2,
    ANVIL_KEY_0,
    ANVIL_KEY_1,
    ANVIL_KEY_2,
    CERTIFICATE_EXPIRES_AT,
    CERTIFICATE_ISSUED_AT,
    CERTIFICATE_STATION_REFERENCE,
    VECTOR_CHAIN_ID,
    VECTOR_CONTRACT,
    certificate_fixture_text,
)

REPO_ROOT = Path(__file__).resolve().parents[2]
FIXTURE_PATH = REPO_ROOT / "shared" / "fixtures" / "station-certificate.json"
SCHEMA_PATH = REPO_ROOT / "shared" / "schemas" / "station-certificate.schema.json"
SIGNATURE_VECTOR_PATH = REPO_ROOT / "shared" / "fixtures" / "signature-vector.json"


@pytest.fixture(scope="module")
def fixture() -> dict:
    assert FIXTURE_PATH.is_file(), f"brak fixture'u certyfikatu: {FIXTURE_PATH}"
    return json.loads(FIXTURE_PATH.read_text(encoding="utf-8"))


@pytest.fixture(scope="module")
def schema() -> dict:
    jsonschema = pytest.importorskip("jsonschema")
    document = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
    jsonschema.Draft202012Validator.check_schema(document)
    return document


def test_fixture_is_generated_reproducibly() -> None:
    assert FIXTURE_PATH.read_text(encoding="utf-8") == certificate_fixture_text()


def test_certificate_node_matches_the_schema(fixture: dict, schema: dict) -> None:
    jsonschema = pytest.importorskip("jsonschema")
    jsonschema.Draft202012Validator(schema).validate(fixture["certificate"])


def test_fixture_contains_no_private_keys() -> None:
    text = FIXTURE_PATH.read_text(encoding="utf-8").lower()
    for key in (ANVIL_KEY_0, ANVIL_KEY_1, ANVIL_KEY_2):
        assert format(key, "064x") not in text


def test_fixture_hashes_and_signature_are_consistent(fixture: dict) -> None:
    certificate = StationCertificate(**fixture["certificate"])
    payload = certificate.payload
    block = fixture["certificateSignature"]

    assert certificate.certificateVersion == fixture["fixtureVersion"] == 1
    assert certificate.payloadHash == certificate_payload_hash(payload)
    assert fixture["canonicalPayloadJcs"] == certificate_payload_text(payload)
    assert block["message"] == {"payloadHash": certificate.payloadHash}
    assert block["signature"] == certificate.signature
    assert block["types"] == {
        "EIP712Domain": [
            {"name": "name", "type": "string"},
            {"name": "version", "type": "string"},
            {"name": "chainId", "type": "uint256"},
            {"name": "verifyingContract", "type": "address"},
        ],
        "StationCertificate": [{"name": "payloadHash", "type": "bytes32"}],
    }
    assert block["digest"] == digest(
        "StationCertificate", certificate_domain(payload), certificate_message(payload)
    )
    assert recovered_issuer(certificate) == block["signer"] == ANVIL_ADDRESS_0
    assert verify_certificate(
        certificate, trusted_issuers=[ANVIL_ADDRESS_0], now=CERTIFICATE_ISSUED_AT
    ) == ANVIL_ADDRESS_0.lower()


def test_registry_call_arguments_follow_the_payload(fixture: dict) -> None:
    payload = CertificatePayload(**fixture["certificate"]["payload"])
    call = fixture["registryCall"]

    assert call == {
        "station": payload.stationAddress,
        "stationId": payload.stationId,
        "expiresAt": payload.expiresAt,
        "stageMask": stage_mask(payload.allowedStageIds),
    }
    assert call["stageMask"] == 0x0F
    assert call["stationId"] == keccak256_text(CERTIFICATE_STATION_REFERENCE)
    assert call["expiresAt"] == CERTIFICATE_EXPIRES_AT


def test_fixture_agrees_with_the_signature_vector(fixture: dict) -> None:
    """Ten sam deployment, stacja i sieć; domena certyfikatu jest świadomie inna."""
    vector = json.loads(SIGNATURE_VECTOR_PATH.read_text(encoding="utf-8"))
    payload = fixture["certificate"]["payload"]
    domain = fixture["domain"]
    stage_domain = vector["domain"]

    assert fixture["domainTypeString"] == vector["domainTypeString"]
    assert domain["chainId"] == stage_domain["chainId"] == VECTOR_CHAIN_ID
    assert domain["version"] == stage_domain["version"] == "1"
    assert domain["verifyingContract"] == stage_domain["verifyingContract"]
    # Rozdzielne nazwy domen uniemożliwiają użycie podpisu certyfikatu jako StageCompletion.
    assert domain["name"] == "SupplyChainStationRegistry"
    assert stage_domain["name"] == "SupplyChain"

    assert payload["stationAddress"] == vector["report"]["stationAddress"]
    assert payload["issuedAt"] == vector["report"]["timestamp"]
    assert payload["verifyingContract"] == VECTOR_CONTRACT.lower()


def test_issuer_is_a_separate_account(fixture: dict) -> None:
    payload = fixture["certificate"]["payload"]
    assert payload["issuerAddress"] == ANVIL_ADDRESS_0.lower()
    assert payload["issuerAddress"] != payload["stationAddress"]
    assert payload["issuerAddress"] not in {ANVIL_ADDRESS_1.lower(), ANVIL_ADDRESS_2.lower()}
    assert fixture["certificateSignature"]["signer"] != payload["stationAddress"]


def invalid_certificate(fixture: dict, case: str) -> dict:
    """Obwiednia certyfikatu z jedną celową usterką (testy negatywne schematu)."""
    envelope = copy.deepcopy(fixture["certificate"])
    payload = envelope["payload"]

    cases = {
        "extra-payload-field": lambda: payload.update(extraField=1),
        "extra-envelope-field": lambda: envelope.update(status="ACTIVE"),
        "missing-certificate-id": lambda: payload.pop("certificateId"),
        "missing-signature": lambda: envelope.pop("signature"),
        "address-uppercase": lambda: payload.update(stationAddress=payload["stationAddress"].upper()),
        "short-signature": lambda: envelope.update(signature="0x" + "11" * 64),
        "empty-stage-ids": lambda: payload.update(allowedStageIds=[]),
        "duplicate-stage-ids": lambda: payload.update(allowedStageIds=[0, 0]),
        "stage-out-of-range": lambda: payload.update(allowedStageIds=[0, 4]),
        "chain-id-zero": lambda: payload.update(chainId=0),
        "issued-at-as-string": lambda: payload.update(issuedAt="1800000000"),
        "schema-version-two": lambda: payload.update(schemaVersion=2),
        "certificate-version-two": lambda: envelope.update(certificateVersion=2),
        "payload-hash-not-hex": lambda: envelope.update(payloadHash="nie-jest-hashem"),
    }
    cases[case]()
    return envelope


@pytest.mark.parametrize(
    "case",
    [
        "extra-payload-field",
        "extra-envelope-field",
        "missing-certificate-id",
        "missing-signature",
        "address-uppercase",
        "short-signature",
        "empty-stage-ids",
        "duplicate-stage-ids",
        "stage-out-of-range",
        "chain-id-zero",
        "issued-at-as-string",
        "schema-version-two",
        "certificate-version-two",
        "payload-hash-not-hex",
    ],
)
def test_schema_rejects_invalid_certificates(fixture: dict, schema: dict, case: str) -> None:
    jsonschema = pytest.importorskip("jsonschema")
    validator = jsonschema.Draft202012Validator(schema)
    document = invalid_certificate(fixture, case)
    assert list(validator.iter_errors(document)), f"schemat nie odrzucił przypadku: {case}"


def test_cli_exports_the_certificate_fixture(
    tmp_path: Path, capsys: pytest.CaptureFixture[str]
) -> None:
    target = tmp_path / "certificate.json"
    assert main(["--export-certificate-fixture", str(target)]) == EXIT_OK
    assert "certyfikat" in capsys.readouterr().out
    assert target.read_text(encoding="utf-8") == certificate_fixture_text()
