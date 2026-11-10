"""Testy trybu `--scenario` w CLI: preflight `--dry-run` bez sieci."""

from __future__ import annotations

import json
import os
from pathlib import Path

import pytest
from supply_chain_simulator.certificate import sign_certificate
from supply_chain_simulator.cli import EXIT_FAILURE, EXIT_OK, EXIT_USAGE, main
from supply_chain_simulator.identity import key_file_payload
from supply_chain_simulator.scenario import default_scenarios_directory
from supply_chain_simulator.vector import (
    ANVIL_KEY_0,
    ANVIL_KEY_1,
    VECTOR_CONTRACT,
    certificate_payload,
)


@pytest.fixture
def station_files(tmp_path: Path) -> tuple[Path, Path]:
    key_path = tmp_path / "station-key.json"
    key_path.write_text(json.dumps(key_file_payload(ANVIL_KEY_1)), encoding="utf-8")
    os.chmod(key_path, 0o600)
    certificate, _ = sign_certificate(certificate_payload(), ANVIL_KEY_0)
    cert_path = tmp_path / "station-certificate.json"
    cert_path.write_text(json.dumps(certificate.model_dump(mode="json")), encoding="utf-8")
    return key_path, cert_path


def test_dry_run_validates_and_prints_the_plan(
    station_files: tuple[Path, Path], capsys: pytest.CaptureFixture[str]
) -> None:
    key_path, cert_path = station_files
    code = main(
        [
            "--scenario",
            str(default_scenarios_directory() / "ebike-happy-path.yaml"),
            "--dry-run",
            "--station-key",
            str(key_path),
            "--certificate",
            str(cert_path),
            "--verifying-contract",
            VECTOR_CONTRACT,
        ]
    )
    out = capsys.readouterr().out
    assert code == EXIT_OK
    assert "[dry-run]" in out
    assert "etap 0" in out and "etap 1" in out


def test_dry_run_does_not_require_backend_or_rpc(
    station_files: tuple[Path, Path],
) -> None:
    key_path, cert_path = station_files
    code = main(
        [
            "--scenario",
            str(default_scenarios_directory() / "ebike-qa-fail.yaml"),
            "--dry-run",
            "--station-key",
            str(key_path),
            "--certificate",
            str(cert_path),
            "--verifying-contract",
            VECTOR_CONTRACT,
        ]
    )
    assert code == EXIT_OK


def test_dry_run_without_certificate_is_a_usage_error(
    station_files: tuple[Path, Path],
) -> None:
    key_path, _ = station_files
    code = main(
        [
            "--scenario",
            "scenario.yaml",
            "--dry-run",
            "--station-key",
            str(key_path),
            "--verifying-contract",
            VECTOR_CONTRACT,
        ]
    )
    assert code == EXIT_USAGE


def test_missing_scenario_file_is_reported(
    station_files: tuple[Path, Path], tmp_path: Path
) -> None:
    key_path, cert_path = station_files
    code = main(
        [
            "--scenario",
            str(tmp_path / "brak.yaml"),
            "--dry-run",
            "--station-key",
            str(key_path),
            "--certificate",
            str(cert_path),
            "--verifying-contract",
            VECTOR_CONTRACT,
        ]
    )
    assert code == EXIT_FAILURE
