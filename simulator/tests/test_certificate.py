"""Testy payloadu i podpisu certyfikatu stacji (DEC-13).

Sprawdzają model, kanoniczny hash, podpis wystawcy, weryfikację fail-closed oraz
to, że podpis certyfikatu nie działa jako `StageCompletion`. Wartości
oczekiwane pochodzą z niezależnego `cast wallet sign --data` (Foundry), tak samo
jak przy wektorze podpisu.
"""

from __future__ import annotations

import pytest
from pydantic import ValidationError
from supply_chain_simulator.canonical import canonical_text
from supply_chain_simulator.certificate import (
    CERTIFICATE_DOMAIN_NAME,
    CERTIFICATE_DOMAIN_VERSION,
    CertificateError,
    certificate_domain,
    certificate_id,
    certificate_message,
    certificate_payload_hash,
    certificate_typed_data,
    recovered_issuer,
    sign_certificate,
    stage_mask,
    station_id,
    verify_certificate,
)
from supply_chain_simulator.models import (
    CertificatePayload,
    StageCompletionMessage,
    certificate_payload_dict,
)
from supply_chain_simulator.signing import digest, recover_signer
from supply_chain_simulator.vector import (
    ANVIL_ADDRESS_0,
    ANVIL_ADDRESS_1,
    ANVIL_KEY_0,
    ANVIL_KEY_1,
    CERTIFICATE_ALLOWED_STAGE_IDS,
    CERTIFICATE_EXPIRES_AT,
    CERTIFICATE_ISSUED_AT,
    CERTIFICATE_SERIAL,
    CERTIFICATE_STATION_REFERENCE,
    VECTOR_CHAIN_ID,
    VECTOR_CONTRACT,
    certificate_payload,
    vector_domain,
)

# Wartości niezależnie potwierdzone przez `cast` (Foundry 1.8.7) dla tego payloadu.
EXPECTED_STATION_ID = "0x071dc5a525d6ea42c4b169703ea5c1776b953512942daac912791c213858f46c"
EXPECTED_CERTIFICATE_ID = "0xd6337752b30f2464c72077439190dafc2d68d9f20fe3bdf436d6e9b2c1c3fd3e"
EXPECTED_PAYLOAD_HASH = "0x8a87341f0d3246663324c2ea77d4f6c28dde9dcb7bc78185b74077fac05de433"
EXPECTED_DIGEST = "0x02418a81d9f703752852b9eeb8379ba9957a8da197863ade03e1e2d6df066afe"
EXPECTED_SIGNATURE = (
    "0x90a23cff883955a41f932d0cdc6661f10654323fbd2dad0c14ad82bc7eabbf12"
    "39b0063eceb0fbee5ea5983abe1d3b016205427803f5038e6d291af6448eb0fa1c"
)


def payload(**overrides: object) -> CertificatePayload:
    """Payload testowy z możliwością podmiany pól w testach mutacji."""
    base = certificate_payload().model_dump(mode="json")
    base.update(overrides)
    return CertificatePayload(**base)


def test_payload_matches_the_agreed_values() -> None:
    certificate = certificate_payload()
    assert certificate.schemaVersion == 1
    assert certificate.stationId == station_id(CERTIFICATE_STATION_REFERENCE)
    assert certificate.stationId == EXPECTED_STATION_ID
    assert certificate.certificateId == certificate_id(CERTIFICATE_SERIAL)
    assert certificate.certificateId == EXPECTED_CERTIFICATE_ID
    assert certificate.stationAddress == ANVIL_ADDRESS_1.lower()
    assert certificate.issuerAddress == ANVIL_ADDRESS_0.lower()
    assert certificate.chainId == VECTOR_CHAIN_ID
    assert certificate.verifyingContract == VECTOR_CONTRACT.lower()
    assert certificate.allowedStageIds == CERTIFICATE_ALLOWED_STAGE_IDS
    assert certificate.expiresAt == CERTIFICATE_EXPIRES_AT
    assert certificate.expiresAt - certificate.issuedAt == 365 * 24 * 3600


def test_payload_keeps_addresses_in_canonical_lowercase() -> None:
    certificate = payload(stationAddress="0x" + ANVIL_ADDRESS_1[2:].upper())
    assert certificate.stationAddress == ANVIL_ADDRESS_1.lower()


def test_payload_hash_follows_jcs_and_keccak() -> None:
    certificate = certificate_payload()
    text = canonical_text(certificate_payload_dict(certificate))
    assert certificate_payload_hash(certificate) == EXPECTED_PAYLOAD_HASH
    # JCS (RFC 8785) sortuje klucze leksykograficznie po UTF-16.
    assert text.index('"allowedStageIds"') < text.index('"certificateId"')
    assert text.index('"issuerAddress"') < text.index('"schemaVersion"')
    assert "\n" not in text


def test_certificate_domain_comes_from_the_payload() -> None:
    domain = certificate_domain(certificate_payload())
    assert domain.name == CERTIFICATE_DOMAIN_NAME == "SupplyChainStationRegistry"
    assert domain.version == CERTIFICATE_DOMAIN_VERSION == "1"
    assert domain.chainId == VECTOR_CHAIN_ID
    assert domain.verifyingContract == VECTOR_CONTRACT.lower()


def test_stage_mask_matches_the_contract_encoding() -> None:
    assert stage_mask(CERTIFICATE_ALLOWED_STAGE_IDS) == 0x0F
    assert stage_mask([2]) == 0x04
    assert stage_mask([0, 3]) == 0x09


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("schemaVersion", 2),
        ("certificateId", "0x" + "11" * 31),
        ("stationId", "0x" + "22" * 33),
        ("stationAddress", "0x123"),
        ("issuerAddress", "nie-adres"),
        ("chainId", 0),
        ("chainId", True),
        ("verifyingContract", "0x" + "aa" * 20 + "bb"),
        ("issuedAt", 1.5),
        ("expiresAt", -1),
        ("allowedStageIds", []),
        ("allowedStageIds", [0, 0]),
        ("allowedStageIds", [1, 0]),
        ("allowedStageIds", [0, 1, 4]),
        ("unknownField", 1),
    ],
)
def test_payload_rejects_invalid_values(field: str, value: object) -> None:
    with pytest.raises(ValidationError):
        payload(**{field: value})


@pytest.mark.parametrize("expires_at", [CERTIFICATE_ISSUED_AT, CERTIFICATE_ISSUED_AT - 1])
def test_payload_requires_expires_at_after_issued_at(expires_at: int) -> None:
    with pytest.raises(ValidationError):
        payload(expiresAt=expires_at)


def test_typed_data_uses_a_dedicated_primary_type() -> None:
    typed = certificate_typed_data(certificate_payload())
    assert typed["primaryType"] == "StationCertificate"
    assert list(typed["types"]) == ["EIP712Domain", "StationCertificate"]
    assert typed["types"]["StationCertificate"] == [{"name": "payloadHash", "type": "bytes32"}]
    assert typed["message"] == {"payloadHash": EXPECTED_PAYLOAD_HASH}


def test_signed_certificate_verifies_and_matches_cast() -> None:
    certificate, signed = sign_certificate(certificate_payload(), ANVIL_KEY_0)
    assert certificate.signature == signed.signature == EXPECTED_SIGNATURE
    assert certificate.payloadHash == EXPECTED_PAYLOAD_HASH
    assert signed.digest == EXPECTED_DIGEST
    assert signed.signer == ANVIL_ADDRESS_0
    assert recovered_issuer(certificate) == ANVIL_ADDRESS_0
    assert verify_certificate(certificate) == ANVIL_ADDRESS_0.lower()


def test_verify_checks_the_issuer_allowlist() -> None:
    certificate, _ = sign_certificate(certificate_payload(), ANVIL_KEY_0)
    assert verify_certificate(certificate, trusted_issuers=[ANVIL_ADDRESS_0]) == (
        ANVIL_ADDRESS_0.lower()
    )
    with pytest.raises(CertificateError):
        verify_certificate(certificate, trusted_issuers=[ANVIL_ADDRESS_1])


@pytest.mark.parametrize(
    ("now", "valid"),
    [
        (CERTIFICATE_ISSUED_AT - 1, False),
        (CERTIFICATE_ISSUED_AT, True),
        (CERTIFICATE_EXPIRES_AT - 1, True),
        (CERTIFICATE_EXPIRES_AT, False),
    ],
)
def test_verify_checks_the_validity_window(now: int, valid: bool) -> None:
    certificate, _ = sign_certificate(certificate_payload(), ANVIL_KEY_0)
    if valid:
        assert verify_certificate(certificate, now=now) == ANVIL_ADDRESS_0.lower()
    else:
        with pytest.raises(CertificateError):
            verify_certificate(certificate, now=now)


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("certificateId", certificate_id("CERT-2026-0002")),
        ("stationId", station_id("ST-002")),
        ("issuerAddress", "0x" + "cd" * 20),
        ("chainId", 80002),
        ("verifyingContract", "0x" + "ab" * 20),
        ("issuedAt", CERTIFICATE_ISSUED_AT + 1),
        ("expiresAt", CERTIFICATE_EXPIRES_AT + 1),
        ("allowedStageIds", [0, 1, 2]),
    ],
)
def test_every_payload_mutation_changes_the_hash(field: str, value: object) -> None:
    certificate, _ = sign_certificate(certificate_payload(), ANVIL_KEY_0)
    mutated = payload(**{field: value})
    assert certificate_payload_hash(mutated) != certificate.payloadHash


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("certificateId", certificate_id("CERT-2026-0002")),
        ("stationId", station_id("ST-002")),
        ("chainId", 80002),
        ("allowedStageIds", [0, 1, 2]),
    ],
)
def test_mutated_payload_invalidates_the_signature(field: str, value: object) -> None:
    certificate, _ = sign_certificate(certificate_payload(), ANVIL_KEY_0)
    invalid = certificate.model_copy(update={"payload": payload(**{field: value})})
    with pytest.raises(CertificateError):
        verify_certificate(invalid)


def test_tampered_payload_hash_is_rejected() -> None:
    certificate, _ = sign_certificate(certificate_payload(), ANVIL_KEY_0)
    tampered = certificate.model_copy(update={"payloadHash": "0x" + "33" * 32})
    with pytest.raises(CertificateError):
        verify_certificate(tampered)


def test_signature_of_another_issuer_is_rejected() -> None:
    certificate, _ = sign_certificate(certificate_payload(), ANVIL_KEY_0)
    _, other_signed = sign_certificate(payload(issuerAddress=ANVIL_ADDRESS_1), ANVIL_KEY_1)
    tampered = certificate.model_copy(update={"signature": other_signed.signature})
    with pytest.raises(CertificateError):
        verify_certificate(tampered)


def test_station_key_cannot_issue_a_certificate() -> None:
    """Klucz stacji nie jest kluczem wystawcy, nawet gdy podpis jest poprawny."""
    certificate, _ = sign_certificate(certificate_payload(), ANVIL_KEY_1)
    assert recovered_issuer(certificate) != certificate.payload.issuerAddress
    with pytest.raises(CertificateError):
        verify_certificate(certificate)


def test_certificate_signature_is_not_a_stage_completion_signature() -> None:
    certificate, _ = sign_certificate(certificate_payload(), ANVIL_KEY_0)
    domain = vector_domain()
    completion = StageCompletionMessage(
        batchId="0x" + "44" * 32,
        stageId=2,
        reportHash="0x" + "55" * 32,
        qaPassed=True,
        deadline=CERTIFICATE_ISSUED_AT,
    )
    assert digest("StageCompletion", domain, completion) != EXPECTED_DIGEST
    recovered = recover_signer("StageCompletion", domain, completion, certificate.signature)
    assert recovered.lower() != certificate.payload.issuerAddress
    assert recovered_issuer(certificate) == ANVIL_ADDRESS_0


def test_certificate_digest_matches_the_independent_cast_value() -> None:
    payload_value = certificate_payload()
    message = certificate_message(payload_value)
    assert digest("StationCertificate", certificate_domain(payload_value), message) == (
        EXPECTED_DIGEST
    )

    other_domain = certificate_domain(payload(chainId=80002))
    assert digest("StationCertificate", other_domain, message) != EXPECTED_DIGEST
