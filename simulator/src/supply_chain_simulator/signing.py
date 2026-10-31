"""Podpisy EIP-712 `StageCompletion` i `StageApproval` (bez nonce).

Typy i kolejność pól są częścią specyfikacji (`docs/protocol.md`, EIP-712,
zmiany v2: V2-01, V2-03, V2-10). Nie używamy `encode_defunct`/`personal_sign`
jako zamiennika EIP-712.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any

from eth_account import Account
from eth_account.messages import SignableMessage, encode_typed_data

from supply_chain_simulator.canonical import keccak256_hex
from supply_chain_simulator.models import (
    Eip712Domain,
    StageApprovalMessage,
    StageCompletionMessage,
    message_payload,
)

EIP712_DOMAIN_TYPE_STRING = (
    "EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)"
)
STAGE_COMPLETION_TYPE_STRING = (
    "StageCompletion(bytes32 batchId,uint32 stageId,bytes32 reportHash,bool qaPassed,uint256 deadline)"
)
STAGE_APPROVAL_TYPE_STRING = (
    "StageApproval(bytes32 batchId,uint32 stageId,bytes32 reportHash,uint256 deadline)"
)

EIP712_DOMAIN_FIELDS: tuple[dict[str, str], ...] = (
    {"name": "name", "type": "string"},
    {"name": "version", "type": "string"},
    {"name": "chainId", "type": "uint256"},
    {"name": "verifyingContract", "type": "address"},
)
STAGE_COMPLETION_FIELDS: tuple[dict[str, str], ...] = (
    {"name": "batchId", "type": "bytes32"},
    {"name": "stageId", "type": "uint32"},
    {"name": "reportHash", "type": "bytes32"},
    {"name": "qaPassed", "type": "bool"},
    {"name": "deadline", "type": "uint256"},
)
STAGE_APPROVAL_FIELDS: tuple[dict[str, str], ...] = (
    {"name": "batchId", "type": "bytes32"},
    {"name": "stageId", "type": "uint32"},
    {"name": "reportHash", "type": "bytes32"},
    {"name": "deadline", "type": "uint256"},
)

_PRIMARY_TYPES: dict[str, tuple[dict[str, str], ...]] = {
    "StageCompletion": STAGE_COMPLETION_FIELDS,
    "StageApproval": STAGE_APPROVAL_FIELDS,
}
_TYPE_STRINGS: dict[str, str] = {
    "StageCompletion": STAGE_COMPLETION_TYPE_STRING,
    "StageApproval": STAGE_APPROVAL_TYPE_STRING,
}


def eip712_types(primary_type: str) -> dict[str, list[dict[str, str]]]:
    """Zwraca definicję typów EIP-712 (domena + typ główny)."""
    fields = _PRIMARY_TYPES.get(primary_type)
    if fields is None:
        raise ValueError(f"nieznany typ podpisu: {primary_type!r}")
    return {"EIP712Domain": list(EIP712_DOMAIN_FIELDS), primary_type: list(fields)}


def typed_data(
    primary_type: str,
    domain: Eip712Domain,
    message: StageCompletionMessage | StageApprovalMessage,
) -> dict[str, Any]:
    """Buduje pełną strukturę `full_message` dla kodera EIP-712."""
    return {
        "types": eip712_types(primary_type),
        "primaryType": primary_type,
        "domain": domain.model_dump(mode="json"),
        "message": message_payload(message),
    }


def encode(
    primary_type: str,
    domain: Eip712Domain,
    message: StageCompletionMessage | StageApprovalMessage,
) -> SignableMessage:
    """Koduje struktury do wiadomości EIP-712 (bez podpisywania)."""
    return encode_typed_data(full_message=typed_data(primary_type, domain, message))


def message_digest(encoded: SignableMessage) -> str:
    """Digest wiadomości: `keccak(0x19 || version || header || body)`.

    Dla EIP-712 `version` to `0x01`, `header` jest pusty, a `body` zawiera
    `domainSeparator || structHash`. eth-account 0.14 nie udostępnia gotowego
    digestu (liczy go funkcją prywatną), więc liczymy go jawnie tym samym
    Keccakiem co reszta kodu; zgodność potwierdza wspólny wektor z Solidity.
    """
    joined = b"\x19" + bytes(encoded.version) + bytes(encoded.header) + bytes(encoded.body)
    return keccak256_hex(joined)


def digest(
    primary_type: str,
    domain: Eip712Domain,
    message: StageCompletionMessage | StageApprovalMessage,
) -> str:
    """Zwraca digest EIP-712 (`0x` + 64 hex) - ten sam, który liczy kontrakt."""
    return message_digest(encode(primary_type, domain, message))


@dataclass(frozen=True)
class SignedTypedData:
    """Wynik podpisu: digest, podpis i adres podpisującego."""

    primaryType: str
    message: dict[str, Any]
    digest: str
    signature: str
    signer: str

    def as_dict(self) -> dict[str, Any]:
        """Reprezentacja do wspólnego wektora (pola wymagane przez testy)."""
        return {
            "typeString": _TYPE_STRINGS[self.primaryType],
            "types": eip712_types(self.primaryType),
            "message": self.message,
            "digest": self.digest,
            "signature": self.signature,
            "signer": self.signer,
        }


def sign(
    primary_type: str,
    domain: Eip712Domain,
    message: StageCompletionMessage | StageApprovalMessage,
    private_key: int,
) -> SignedTypedData:
    """Podpisuje struktury kluczem prywatnym (deterministycznie, RFC 6979)."""
    encoded = encode(primary_type, domain, message)
    signed = Account.sign_message(encoded, private_key=private_key)
    return SignedTypedData(
        primaryType=primary_type,
        message=message_payload(message),
        digest=message_digest(encoded),
        signature="0x" + bytes(signed.signature).hex(),
        signer=Account.from_key(private_key).address,
    )


def sign_stage_completion(
    domain: Eip712Domain, message: StageCompletionMessage, private_key: int
) -> SignedTypedData:
    """Podpis stacji pod `StageCompletion`."""
    return sign("StageCompletion", domain, message, private_key)


def sign_stage_approval(
    domain: Eip712Domain, message: StageApprovalMessage, private_key: int
) -> SignedTypedData:
    """Podpis operatora zmiany pod `StageApproval`."""
    return sign("StageApproval", domain, message, private_key)


def recover_signer(
    primary_type: str,
    domain: Eip712Domain,
    message: StageCompletionMessage | StageApprovalMessage,
    signature: str,
) -> str:
    """Odzyskuje adres podpisującego z podpisu (checksummed)."""
    return Account.recover_message(encode(primary_type, domain, message), signature=signature)
