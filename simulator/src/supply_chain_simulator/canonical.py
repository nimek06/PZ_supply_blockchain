"""Kanonizacja JCS (RFC 8785) i Keccak-256 dla raportu v1.

Kolejność zgodna z `docs/protocol.md`: walidacja modelu -> normalizacja adresu
(robi to model) -> bajty UTF-8 według RFC 8785 -> Keccak-256 (nie SHA3-256).
Samo sortowanie kluczy albo domyślny serializer nie gwarantuje JCS, dlatego
używamy biblioteki `rfc8785`, a nie własnej implementacji.
"""

from __future__ import annotations

from collections.abc import Mapping
from typing import Any

import rfc8785
from eth_utils import keccak

from supply_chain_simulator.models import Report, report_payload


def canonical_bytes(payload: Mapping[str, Any]) -> bytes:
    """Zwraca kanoniczne bajty UTF-8 (RFC 8785) dla obiektu JSON.

    Podnosi `rfc8785.CanonicalizationError`, gdy payload zawiera typ
    nieobsługiwany przez JCS (np. `float`). Modele Pydantic nie dopuszczają
    takich wartości, więc w normalnym przepływie nie występuje.
    """
    if not isinstance(payload, Mapping):
        raise TypeError("payload musi być mapowaniem (obiektem JSON)")
    return rfc8785.dumps(payload)


def canonical_text(payload: Mapping[str, Any]) -> str:
    """Jak `canonical_bytes`, ale zwraca tekst UTF-8 (do pliku/fixture)."""
    return canonical_bytes(payload).decode("utf-8")


def keccak256_hex(data: bytes) -> str:
    """Keccak-256 zwrócony jako `0x` + 64 znaki hex."""
    return "0x" + bytes(keccak(data)).hex()


def report_hash(report: Report) -> str:
    """`reportHash` = Keccak-256 kanonicznych bajtów raportu."""
    return keccak256_hex(canonical_bytes(report_payload(report)))
