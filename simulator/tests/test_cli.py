"""Testy szkieletu CLI symulatora (T006).

Nie wymagają sieci ani sekretów. Sprawdzają kod wyjścia, brak efektów
ubocznych importu (brak klientów sieciowych w ``sys.modules``) oraz działanie
obu wejść: ``python -m supply_chain_simulator`` i skryptu konsolowego.
"""

from __future__ import annotations

import shutil
import subprocess
import sys

import pytest
from supply_chain_simulator import __version__
from supply_chain_simulator.cli import EXIT_OK, EXIT_USAGE, build_parser, main

MODULE = "supply_chain_simulator"
CONSOLE_SCRIPT = "supply-chain-simulator"


def run_python(*args: str) -> subprocess.CompletedProcess[str]:
    """Uruchamia polecenie w tym samym interpreterze (venv) bez sieci."""
    return subprocess.run(
        [sys.executable, *args],
        capture_output=True,
        text=True,
        timeout=60,
        check=False,
    )


def test_help_exits_zero(capsys: pytest.CaptureFixture[str]) -> None:
    with pytest.raises(SystemExit) as excinfo:
        main(["--help"])
    assert excinfo.value.code == EXIT_OK
    assert "usage" in capsys.readouterr().out


def test_version_exits_zero_and_prints_version(capsys: pytest.CaptureFixture[str]) -> None:
    with pytest.raises(SystemExit) as excinfo:
        main(["--version"])
    assert excinfo.value.code == EXIT_OK
    assert __version__ in capsys.readouterr().out


def test_no_arguments_is_usage_error(capsys: pytest.CaptureFixture[str]) -> None:
    assert main([]) == EXIT_USAGE
    assert "usage" in capsys.readouterr().err


def test_unknown_argument_is_usage_error() -> None:
    with pytest.raises(SystemExit) as excinfo:
        main(["--nie-istnieje"])
    assert excinfo.value.code == EXIT_USAGE


def test_build_parser_has_no_side_effects() -> None:
    parser = build_parser()
    assert parser.prog == CONSOLE_SCRIPT


def test_import_does_not_load_network_clients() -> None:
    code = (
        "import sys\n"
        "import supply_chain_simulator as simulator\n"
        "heavy = sorted(m for m in ('httpx', 'eth_account') if m in sys.modules)\n"
        "print(simulator.__version__)\n"
        "print(','.join(heavy))\n"
    )
    result = run_python("-c", code)
    assert result.returncode == EXIT_OK, result.stderr
    version, heavy = result.stdout.splitlines()
    assert version.strip() == __version__
    assert heavy == "", f"import pakietu zaladowal: {heavy}"


def test_module_entry_point_help() -> None:
    result = run_python("-m", MODULE, "--help")
    assert result.returncode == EXIT_OK, result.stderr
    assert "usage" in result.stdout


def test_module_entry_point_without_arguments_fails() -> None:
    result = run_python("-m", MODULE)
    assert result.returncode == EXIT_USAGE
    assert "usage" in result.stderr


def test_console_script_help() -> None:
    script = shutil.which(CONSOLE_SCRIPT)
    if script is None:  # pragma: no cover - brak instalacji w venv
        pytest.skip(f"brak skryptu {CONSOLE_SCRIPT} w PATH; zainstaluj pakiet w venv")
    result = subprocess.run(
        [script, "--help"],
        capture_output=True,
        text=True,
        timeout=60,
        check=False,
    )
    assert result.returncode == EXIT_OK, result.stderr
    assert "usage" in result.stdout
