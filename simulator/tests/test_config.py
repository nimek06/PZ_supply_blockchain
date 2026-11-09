"""Testy konfiguracji przebiegu symulatora (T020)."""

from __future__ import annotations

from pathlib import Path

import pytest
from supply_chain_simulator.config import ConfigError, SimulatorConfig

VALID: dict = {
    "backend_url": "http://127.0.0.1:8080",
    "rpc_url": "http://127.0.0.1:8545",
    "chain_id": 31337,
    "verifying_contract": "0x" + "ab" * 20,
    "station_key_path": Path("/tmp/key.json"),
    "certificate_path": Path("/tmp/cert.json"),
    "seed": 1,
}


def test_valid_config_has_defaults() -> None:
    config = SimulatorConfig(**VALID)
    assert config.timeout == 10.0
    assert config.token is None
    assert config.chain_id == 31337


def test_token_is_kept_in_memory_only() -> None:
    config = SimulatorConfig(**{**VALID, "token": "secret-token"})
    assert config.token == "secret-token"


@pytest.mark.parametrize(
    ("field", "value"),
    [
        ("backend_url", "ftp://127.0.0.1"),
        ("backend_url", "http://"),
        ("rpc_url", "127.0.0.1:8545"),
        ("chain_id", 0),
        ("chain_id", True),
        ("verifying_contract", "0x123"),
        ("seed", -1),
        ("seed", True),
        ("timeout", 0),
        ("timeout", -1.0),
        ("token", ""),
        ("token", "   "),
    ],
)
def test_invalid_values_are_rejected(field: str, value: object) -> None:
    with pytest.raises(ConfigError):
        SimulatorConfig(**{**VALID, field: value})
