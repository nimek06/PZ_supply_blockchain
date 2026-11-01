"""Klucz i certyfikat stacji: generowanie, zapis poza repo, wczytanie i gotowość.

Reguły (`simulator/README.md`, `docs/station-identity.md`):
- klucz generujemy lokalnie i nigdy nie wypisujemy go do logów,
- plik klucza ma uprawnienia 0600 i leży poza repozytorium albo w ignorowanym
  katalogu `infrastructure/local/secrets/`,
- certyfikat jest poświadczeniem audytowym: wiąże `stationId` z adresem stacji
  i musi zgadzać się z rejestrem on-chain (`registerStation`),
- symulator nie ma klucza operatora zmiany ani klucza wystawcy.

Moduł celowo nie deklaruje stanu ACTIVE/REVOKED: to wynik weryfikacji
w backendzie razem z odczytem rejestru on-chain, a nie pole w pliku.
"""

from __future__ import annotations

import json
import os
import stat
from collections.abc import Sequence
from dataclasses import dataclass
from pathlib import Path
from typing import Final

from eth_account import Account

from supply_chain_simulator.certificate import CertificateError, verify_certificate
from supply_chain_simulator.models import StationCertificate
from supply_chain_simulator.signing import private_key_hex

#: Uprawnienia pliku klucza: tylko właściciel.
KEY_FILE_MODE: Final = 0o600
#: Katalog w repozytorium na lokalne sekrety (ignorowany przez Git).
SECRETS_DIRECTORY: Final = Path("infrastructure") / "local" / "secrets"


class IdentityError(ValueError):
    """Klucz albo certyfikat stacji nie nadaje się do użycia (fail-closed)."""


@dataclass(frozen=True)
class StationKeyFile:
    """Klucz stacji wczytany z pliku albo przygotowany do zapisu."""

    address: str
    private_key: int
    path: Path


def repo_root() -> Path:
    """Katalog repozytorium na podstawie położenia tego pliku."""
    return Path(__file__).resolve().parents[3]


def secrets_directory() -> Path:
    """Dopuszczalny katalog na lokalne klucze i certyfikaty."""
    return repo_root() / SECRETS_DIRECTORY


def is_allowed_secret_path(path: Path) -> bool:
    """Czy klucz może tu leżeć: poza repo albo w ignorowanym katalogu sekretów."""
    resolved = path.expanduser().resolve()
    allowed = secrets_directory().resolve()
    if resolved == allowed or allowed in resolved.parents:
        return True
    try:
        resolved.relative_to(repo_root())
    except ValueError:
        return True
    return False


def address_for_key(private_key: int) -> str:
    """Adres (checksummed) dla klucza prywatnego.

    Klucz przekazujemy jako pełne 32 bajty (`private_key_hex`), bo `eth-account`
    skraca liczbę całkowitą do `HexBytes` o minimalnej długości i klucz z zerem
    wiodącym przestałby być poprawnym kluczem.
    """
    return Account.from_key(private_key_hex(private_key)).address


def generate_station_key() -> tuple[str, int]:
    """Generuje parę kluczy stacji; zwraca `(address, private_key)`."""
    account = Account.create()
    return account.address, int(account.key.hex(), 16)


def key_file_payload(private_key: int) -> dict[str, str]:
    """Treść pliku klucza: adres i klucz zapisany tylko w tym pliku."""
    return {
        "address": address_for_key(private_key),
        "privateKey": private_key_hex(private_key),
    }


def save_station_key(path: Path, private_key: int) -> StationKeyFile:
    """Zapisuje klucz stacji z prawami 0600 i nie nadpisuje istniejącego pliku.

    Podnosi `IdentityError`, gdy ścieżka leży w repozytorium poza ignorowanym
    katalogiem sekretów albo plik już istnieje (nie nadpisujemy kluczy w użyciu).
    """
    if not is_allowed_secret_path(path):
        raise IdentityError(
            f"klucz stacji nie może leżeć w repozytorium poza {SECRETS_DIRECTORY}"
        )
    target = path.expanduser()
    if target.exists():
        raise IdentityError(f"plik klucza już istnieje: {target}")

    # Treść liczymy przed utworzeniem pliku: niepoprawny klucz nie może zostawić
    # pustego pliku 0600, który zablokowałby ponowną próbę.
    payload = key_file_payload(private_key)
    target.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    descriptor = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, KEY_FILE_MODE)
    with os.fdopen(descriptor, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2, ensure_ascii=False)
        handle.write("\n")
    return StationKeyFile(
        address=address_for_key(private_key), private_key=private_key, path=target
    )


def load_station_key(path: Path) -> StationKeyFile:
    """Wczytuje klucz stacji i sprawdza uprawnienia pliku oraz zgodność adresu."""
    target = path.expanduser()
    if not target.is_file():
        raise IdentityError(f"brak pliku klucza stacji: {target}")

    mode = stat.S_IMODE(target.stat().st_mode)
    if mode & 0o077:
        raise IdentityError(f"plik klucza ma zbyt szerokie uprawnienia: {oct(mode)}")

    try:
        data = json.loads(target.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise IdentityError(f"plik klucza nie jest poprawnym JSON: {exc}") from exc
    if not isinstance(data, dict) or set(data) != {"address", "privateKey"}:
        raise IdentityError("plik klucza musi zawierać dokładnie pola address i privateKey")

    try:
        private_key = int(str(data["privateKey"]), 16)
    except ValueError as exc:
        raise IdentityError("privateKey w pliku klucza nie jest liczbą hex") from exc

    address = address_for_key(private_key)
    if address.lower() != str(data["address"]).lower():
        raise IdentityError("address w pliku klucza nie odpowiada privateKey")
    return StationKeyFile(address=address, private_key=private_key, path=target)


def load_certificate(path: Path) -> StationCertificate:
    """Wczytuje certyfikat stacji z pliku JSON (obwiednia z payloadem i podpisem)."""
    target = path.expanduser()
    if not target.is_file():
        raise IdentityError(f"brak pliku certyfikatu: {target}")
    try:
        return StationCertificate(**json.loads(target.read_text(encoding="utf-8")))
    except (json.JSONDecodeError, ValueError) as exc:
        raise IdentityError(f"certyfikat niezgodny z modelem: {exc}") from exc


def assert_station_ready(
    certificate: StationCertificate,
    station: StationKeyFile,
    *,
    trusted_issuers: Sequence[str] | None = None,
    now: int | None = None,
) -> str:
    """Sprawdza certyfikat i zgodność z lokalnym kluczem stacji.

    Sprawdza poprawny hash i podpis wystawcy, allowlistę wystawców, okno
    ważności oraz to, że certyfikat wskazuje adres z pliku klucza. Zgodność
    z rejestrem on-chain i odwołanie certyfikatu sprawdza backend; brak tego
    potwierdzenia to stan PENDING_ACTIVATION, a nie gotowość do zgłoszeń.
    """
    try:
        verify_certificate(certificate, trusted_issuers=trusted_issuers, now=now)
    except CertificateError as exc:
        raise IdentityError(str(exc)) from exc

    if certificate.payload.stationAddress != station.address.lower():
        raise IdentityError("certyfikat dotyczy innego adresu niż klucz stacji")
    return station.address
