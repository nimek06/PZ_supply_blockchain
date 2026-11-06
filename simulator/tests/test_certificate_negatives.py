"""Testy negatywnych wariantów certyfikatu dla weryfikatora Javy (T010).

`station-certificate-expired.json` - poprawnie podpisany przez zaufanego wystawcę,
ale okno ważności w przeszłości; `station-certificate-untrusted-issuer.json` -
poprawnie podpisany i spójny, ale wystawca spoza allowlisty wdrożenia. Oba mają
być odrzucone mimo poprawnej kryptografii (fail-closed).
"""

from __future__ import annotations

import json
from pathlib import Path

import pytest
from supply_chain_simulator.certificate import (
    CertificateError,
    recovered_issuer,
    verify_certificate,
)
from supply_chain_simulator.models import StationCertificate
from supply_chain_simulator.vector import (
    ANVIL_ADDRESS_0,
    ANVIL_ADDRESS_2,
    ANVIL_KEY_0,
    ANVIL_KEY_1,
    ANVIL_KEY_2,
    CERTIFICATE_EXPIRED_EXPIRES_AT,
    CERTIFICATE_EXPIRED_FIXTURE_NAME,
    CERTIFICATE_EXPIRED_ISSUED_AT,
    CERTIFICATE_UNTRUSTED_ISSUER_FIXTURE_NAME,
    certificate_expired_fixture_text,
    certificate_untrusted_issuer_fixture_text,
)

REPO_ROOT = Path(__file__).resolve().parents[2]
FIXTURES = REPO_ROOT / "shared" / "fixtures"
SCHEMA_PATH = REPO_ROOT / "shared" / "schemas" / "station-certificate.schema.json"
VALID_CERTIFICATE = "station-certificate.json"
NEGATIVE_NAMES = [
    CERTIFICATE_EXPIRED_FIXTURE_NAME,
    CERTIFICATE_UNTRUSTED_ISSUER_FIXTURE_NAME,
]
TRUSTED_ISSUERS = [ANVIL_ADDRESS_0]


def load(name: str) -> dict:
    return json.loads((FIXTURES / name).read_text(encoding="utf-8"))


def certificate_of(name: str) -> StationCertificate:
    return StationCertificate(**load(name)["certificate"])


@pytest.fixture(scope="module")
def schema_validator():
    jsonschema = pytest.importorskip("jsonschema")
    document = json.loads(SCHEMA_PATH.read_text(encoding="utf-8"))
    return jsonschema.Draft202012Validator(document)


@pytest.mark.parametrize("name", NEGATIVE_NAMES)
def test_negative_certificates_match_the_schema(name: str, schema_validator) -> None:
    schema_validator.validate(load(name)["certificate"])


def test_expired_fixture_is_reproducible() -> None:
    text = (FIXTURES / CERTIFICATE_EXPIRED_FIXTURE_NAME).read_text(encoding="utf-8")
    assert text == certificate_expired_fixture_text()


def test_untrusted_fixture_is_reproducible() -> None:
    text = (FIXTURES / CERTIFICATE_UNTRUSTED_ISSUER_FIXTURE_NAME).read_text(encoding="utf-8")
    assert text == certificate_untrusted_issuer_fixture_text()


def test_expired_certificate_is_rejected_at_expiry() -> None:
    certificate = certificate_of(CERTIFICATE_EXPIRED_FIXTURE_NAME)
    with pytest.raises(CertificateError) as error:
        verify_certificate(
            certificate, trusted_issuers=TRUSTED_ISSUERS, now=CERTIFICATE_EXPIRED_EXPIRES_AT
        )
    assert "wygasł" in str(error.value)


def test_expired_certificate_is_accepted_inside_its_window() -> None:
    """Kontrola: odrzucenie wynika z okna ważności, nie z podpisu."""
    certificate = certificate_of(CERTIFICATE_EXPIRED_FIXTURE_NAME)
    issuer = verify_certificate(
        certificate,
        trusted_issuers=TRUSTED_ISSUERS,
        now=CERTIFICATE_EXPIRED_ISSUED_AT + 1,
    )
    assert issuer == ANVIL_ADDRESS_0.lower()


def test_untrusted_issuer_certificate_is_self_consistent() -> None:
    certificate = certificate_of(CERTIFICATE_UNTRUSTED_ISSUER_FIXTURE_NAME)
    assert certificate.payload.issuerAddress == ANVIL_ADDRESS_2.lower()
    assert recovered_issuer(certificate).lower() == ANVIL_ADDRESS_2.lower()
    assert verify_certificate(certificate).lower() == ANVIL_ADDRESS_2.lower()


def test_untrusted_issuer_is_rejected_by_the_allowlist() -> None:
    certificate = certificate_of(CERTIFICATE_UNTRUSTED_ISSUER_FIXTURE_NAME)
    with pytest.raises(CertificateError) as error:
        verify_certificate(certificate, trusted_issuers=TRUSTED_ISSUERS)
    assert "niezaufany wystawca" in str(error.value)


def test_expected_verification_blocks_describe_the_rejection() -> None:
    assert load(CERTIFICATE_EXPIRED_FIXTURE_NAME)["expectedVerification"] == {
        "accepted": False,
        "reason": "expired",
        "checkedAt": CERTIFICATE_EXPIRED_EXPIRES_AT,
    }
    assert load(CERTIFICATE_UNTRUSTED_ISSUER_FIXTURE_NAME)["expectedVerification"] == {
        "accepted": False,
        "reason": "untrusted_issuer",
        "trustedIssuers": [ANVIL_ADDRESS_0.lower()],
    }


def test_untrusted_certificate_uses_a_distinct_serial() -> None:
    untrusted = load(CERTIFICATE_UNTRUSTED_ISSUER_FIXTURE_NAME)["certificate"]["payload"]
    valid = load(VALID_CERTIFICATE)["certificate"]["payload"]
    assert untrusted["certificateId"] != valid["certificateId"]


def test_negative_certificates_contain_no_private_keys() -> None:
    for name in NEGATIVE_NAMES:
        text = (FIXTURES / name).read_text(encoding="utf-8").lower()
        for key in (ANVIL_KEY_0, ANVIL_KEY_1, ANVIL_KEY_2):
            assert format(key, "064x") not in text
