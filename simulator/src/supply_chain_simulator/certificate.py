"""Certyfikat stacji: payload, hash JCS, domena wystawcy i weryfikacja (DEC-13).

Format opisuje `docs/station-identity.md`: publiczne poświadczenie składa się
z `payload` (kanonizowanego JCS i hashowanego Keccakiem-256) oraz podpisu
wystawcy EIP-712 w **osobnej** domenie `SupplyChainStationRegistry` i typie
`StationCertificate(bytes32 payloadHash)`. Podpisu certyfikatu nie wolno użyć
jako `StageCompletion` ani odwrotnie - różne type hashes (`docs/protocol.md`).

Ten moduł sprawdza kryptografię i ważność poświadczenia. Zgodność z rejestrem
on-chain (`registerStation`: adres, `stationId`, `expiresAt`, `stageMask`) oraz
stany REVOKED/BLOCKED należą do kontraktu i backendu - nie są tu deklarowane.
Nazwa domeny i typ są ustalone w DEC-13 (2026-11-02); zmiana wymaga nowej
wersji i zgody osób 1-3.
"""

from __future__ import annotations

from collections.abc import Sequence

from supply_chain_simulator.canonical import (
    canonical_bytes,
    canonical_text,
    keccak256_hex,
    keccak256_text,
)
from supply_chain_simulator.models import (
    CERTIFICATE_ENVELOPE_VERSION,
    CertificatePayload,
    Eip712Domain,
    StationCertificate,
    StationCertificateMessage,
    certificate_payload_dict,
    normalize_address,
)
from supply_chain_simulator.signing import (
    SignedTypedData,
    recover_signer,
    sign_station_certificate,
    typed_data,
)

CERTIFICATE_DOMAIN_NAME = "SupplyChainStationRegistry"
CERTIFICATE_DOMAIN_VERSION = "1"


class CertificateError(ValueError):
    """Certyfikat niespójny: zły hash payloadu, podpis, wystawca albo ważność."""


def certificate_payload_hash(payload: CertificatePayload) -> str:
    """`payloadHash` = Keccak-256 kanonicznych bajtów payloadu (JCS)."""
    return keccak256_hex(canonical_bytes(certificate_payload_dict(payload)))


def certificate_payload_text(payload: CertificatePayload) -> str:
    """Kanoniczny payload certyfikatu jako tekst UTF-8 (do fixture'u i audytu)."""
    return canonical_text(certificate_payload_dict(payload))


def certificate_domain(payload: CertificatePayload) -> Eip712Domain:
    """Domena wystawcy: `chainId` i `verifyingContract` pochodzą z certyfikatu."""
    return Eip712Domain(
        name=CERTIFICATE_DOMAIN_NAME,
        version=CERTIFICATE_DOMAIN_VERSION,
        chainId=payload.chainId,
        verifyingContract=payload.verifyingContract,
    )


def certificate_message(payload: CertificatePayload) -> StationCertificateMessage:
    """Wiadomość EIP-712: hash całego payloadu, bez podpisu i daty odbioru."""
    return StationCertificateMessage(payloadHash=certificate_payload_hash(payload))


def certificate_typed_data(payload: CertificatePayload) -> dict[str, object]:
    """Pełna struktura `full_message` dla typu `StationCertificate`."""
    return typed_data(
        "StationCertificate", certificate_domain(payload), certificate_message(payload)
    )


def station_id(station_reference: str) -> str:
    """`stationId = keccak256(utf8(reference))`, np. `keccak256("ST-001")`.

    Ta sama reguła co w `contracts/test/SupplyChain.t.sol`, żeby certyfikat
    i rejestracja on-chain wskazywały tę samą wartość bajtową.
    """
    return keccak256_text(station_reference)


def certificate_id(serial: str) -> str:
    """`certificateId = keccak256(utf8(serial))` nadanego przez wystawcę."""
    return keccak256_text(serial)


def stage_mask(allowed_stage_ids: Sequence[int]) -> int:
    """`stageMask` w formie kontraktu: bit `i` odpowiada etapowi `i` (V2-04)."""
    mask = 0
    for stage_id in allowed_stage_ids:
        mask |= 1 << stage_id
    return mask


def sign_certificate(
    payload: CertificatePayload, private_key: int
) -> tuple[StationCertificate, SignedTypedData]:
    """Buduje obwiednię certyfikatu i podpisuje ją kluczem wystawcy.

    Zwraca `(certificate, signed)`: obwiednię do zapisu i audytu oraz blok
    podpisu (digest, podpis, adres wystawcy), który trafia też do wspólnego
    fixture'u. Klucz wystawcy nie jest kluczem stacji ani kluczem relayera.
    """
    signed = sign_station_certificate(
        certificate_domain(payload), certificate_message(payload), private_key
    )
    certificate = StationCertificate(
        certificateVersion=CERTIFICATE_ENVELOPE_VERSION,
        payload=payload,
        payloadHash=signed.message["payloadHash"],
        signature=signed.signature,
    )
    return certificate, signed


def recovered_issuer(certificate: StationCertificate) -> str:
    """Odzyskuje adres wystawcy z podpisu zawartego w certyfikacie (checksummed)."""
    return recover_signer(
        "StationCertificate",
        certificate_domain(certificate.payload),
        StationCertificateMessage(payloadHash=certificate.payloadHash),
        certificate.signature,
    )


def verify_certificate(
    certificate: StationCertificate,
    *,
    trusted_issuers: Sequence[str] | None = None,
    now: int | None = None,
) -> str:
    """Sprawdza hash, podpis, allowlistę wystawców i ważność; zwraca wystawcę.

    Każda niezgodność podnosi `CertificateError` (fail-closed, bez cichego
    pominięcia). `trusted_issuers` to allowlista zaufana dla danego wdrożenia
    (`issuerAddress`); `now` w sekundach Uniksa włącza warunek
    `issuedAt <= now < expiresAt`. Zgodności z rejestrem stacji on-chain,
    odwołania certyfikatu i stanu backendu nie da się sprawdzić offline -
    należą do backendu i kontraktu, nie do tego modułu.
    """
    expected_hash = certificate_payload_hash(certificate.payload)
    if expected_hash != certificate.payloadHash:
        raise CertificateError(
            f"payloadHash {certificate.payloadHash} nie odpowiada payloadowi ({expected_hash})"
        )

    declared_issuer = normalize_address(certificate.payload.issuerAddress)
    issuer = recovered_issuer(certificate).lower()
    if issuer != declared_issuer:
        raise CertificateError(f"podpis nie należy do issuerAddress (odzyskany {issuer})")

    if trusted_issuers is not None:
        allowed = {normalize_address(address) for address in trusted_issuers}
        if declared_issuer not in allowed:
            raise CertificateError(f"niezaufany wystawca certyfikatu: {declared_issuer}")

    if now is not None:
        if now < certificate.payload.issuedAt:
            raise CertificateError("certyfikat nie jest jeszcze ważny (issuedAt w przyszłości)")
        if now >= certificate.payload.expiresAt:
            raise CertificateError("certyfikat wygasł (expiresAt już minął)")

    return issuer
