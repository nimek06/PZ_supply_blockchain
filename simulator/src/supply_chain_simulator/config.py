"""Konfiguracja przebiegu symulatora (T020, tydzień 2).

Konfigurację składa CLI (argumenty + zmienne środowiskowe) i przekazuje dalej;
ten moduł tylko waliduje i przechowuje wartości. Sekrety (token Bearer) czytamy
ze zmiennej środowiskowej i trzymamy w pamięci - nie zapisujemy ich w plikach
ani w repozytorium. Klucz stacji i certyfikat przekazujemy jako ścieżki, nie
treści.
"""

from __future__ import annotations

import re
from dataclasses import dataclass
from pathlib import Path

ADDRESS_PATTERN = r"^0x[0-9a-fA-F]{40}$"


class ConfigError(ValueError):
    """Konfiguracja niespójna (fail-closed)."""


def _require_http_url(value: object, field: str) -> None:
    if (
        not isinstance(value, str)
        or not value.startswith(("http://", "https://"))
        or value.rstrip("/") in {"http:", "https:"}
    ):
        raise ConfigError(f"{field} musi być adresem http(s)://")


@dataclass(frozen=True)
class SimulatorConfig:
    """Niezmienny zestaw ustawień jednego przebiegu symulatora.

    Nie zawiera kluczy: `token` to token Bearer z sesji/środowiska, trzymany
    tylko w pamięci; `station_key_path` i `certificate_path` to ścieżki.
    """

    backend_url: str
    rpc_url: str
    chain_id: int
    verifying_contract: str
    station_key_path: Path
    certificate_path: Path
    seed: int
    timeout: float = 10.0
    token: str | None = None

    def __post_init__(self) -> None:
        _require_http_url(self.backend_url, "backend_url")
        _require_http_url(self.rpc_url, "rpc_url")
        if not isinstance(self.chain_id, int) or isinstance(self.chain_id, bool) or self.chain_id < 1:
            raise ConfigError("chain_id musi być dodatnią liczbą całkowitą")
        if not isinstance(self.verifying_contract, str) or not re.fullmatch(
            ADDRESS_PATTERN, self.verifying_contract
        ):
            raise ConfigError("verifying_contract musi być adresem 0x + 40 znaków hex")
        if not isinstance(self.seed, int) or isinstance(self.seed, bool) or self.seed < 0:
            raise ConfigError("seed musi być nieujemną liczbą całkowitą")
        if (
            not isinstance(self.timeout, (int, float))
            or isinstance(self.timeout, bool)
            or self.timeout <= 0
        ):
            raise ConfigError("timeout musi być dodatni")
        if self.token is not None and (
            not isinstance(self.token, str) or not self.token.strip()
        ):
            raise ConfigError("token nie może być pusty; użyj None, gdy go nie ma")
