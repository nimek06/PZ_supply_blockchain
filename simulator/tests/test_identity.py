"""Testy tożsamości stacji: klucz poza repo, certyfikat i tryby CLI (T009/DEC-13).

Klucze w testach to publiczne klucze deweloperskie Anvil albo klucze generowane
w katalogu tymczasowym. Żaden test nie wypisuje ani nie porównuje sekretów
z repozytorium.
"""

from __future__ import annotations

import json
import os
import stat
from pathlib import Path

import pytest
from eth_account import Account
from supply_chain_simulator.certificate import certificate_id, station_id
from supply_chain_simulator.cli import EXIT_FAILURE, EXIT_OK, EXIT_USAGE, main
from supply_chain_simulator.identity import (
    IdentityError,
    StationKeyFile,
    address_for_key,
    assert_station_ready,
    generate_station_key,
    is_allowed_secret_path,
    load_certificate,
    load_station_key,
    repo_root,
    save_station_key,
    secrets_directory,
)
from supply_chain_simulator.vector import (
    ANVIL_ADDRESS_0,
    ANVIL_ADDRESS_1,
    ANVIL_KEY_0,
    ANVIL_KEY_1,
    ANVIL_KEY_2,
    CERTIFICATE_EXPIRES_AT,
    CERTIFICATE_ISSUED_AT,
    CERTIFICATE_SERIAL,
    CERTIFICATE_STATION_REFERENCE,
    VECTOR_CONTRACT,
    load_certificate_fixture,
)

REPO_ROOT = Path(__file__).resolve().parents[2]


def station_key_file(private_key: int) -> StationKeyFile:
    """Klucz stacji bez pliku - do testów gotowości certyfikatu."""
    return StationKeyFile(
        address=address_for_key(private_key), private_key=private_key, path=Path("/nieużywane")
    )


def test_generate_station_key_returns_matching_pair() -> None:
    address, private_key = generate_station_key()
    assert address == address_for_key(private_key)
    assert address.startswith("0x") and len(address) == 42
    assert address != ANVIL_ADDRESS_1


def test_key_with_leading_zero_byte_is_saved_and_loaded(tmp_path: Path) -> None:
    """Klucz z zerem wiodącym nie może stracić bajtu (regresja).

    `eth-account` skraca `int` do `HexBytes` o minimalnej długości, więc adres
    liczymy niezależnie z 32 bajtów i porównujemy z `address_for_key`.
    """
    private_key = int("0x00" + "11" * 31, 16)
    path = tmp_path / "station-key.json"

    saved = save_station_key(path, private_key)
    assert saved.address == Account.from_key(bytes.fromhex("00" + "11" * 31)).address

    loaded = load_station_key(path)
    assert loaded.private_key == private_key
    assert loaded.address == saved.address


def test_save_rejects_invalid_key_without_leaving_a_file(tmp_path: Path) -> None:
    path = tmp_path / "station-key.json"

    with pytest.raises(ValueError):
        save_station_key(path, 0)
    assert path.exists() is False


def test_save_and_load_station_key_round_trip(tmp_path: Path) -> None:
    path = tmp_path / "station-key.json"
    _, private_key = generate_station_key()

    saved = save_station_key(path, private_key)
    assert stat.S_IMODE(path.stat().st_mode) == 0o600

    loaded = load_station_key(path)
    assert loaded.private_key == private_key
    assert loaded.address == saved.address
    assert loaded.path == path


def test_save_refuses_path_inside_the_repository(tmp_path: Path) -> None:
    _, private_key = generate_station_key()
    with pytest.raises(IdentityError):
        save_station_key(repo_root() / "station-key.json", private_key)


def test_only_secrets_directory_is_allowed_inside_the_repository() -> None:
    assert is_allowed_secret_path(secrets_directory() / "station-key.json") is True
    assert is_allowed_secret_path(repo_root() / "station-key.json") is False
    assert is_allowed_secret_path(REPO_ROOT / "shared" / "station-key.json") is False


def test_save_never_overwrites_an_existing_key(tmp_path: Path) -> None:
    path = tmp_path / "station-key.json"
    _, first = generate_station_key()
    _, second = generate_station_key()

    save_station_key(path, first)
    with pytest.raises(IdentityError):
        save_station_key(path, second)
    assert load_station_key(path).private_key == first


def test_load_rejects_wide_permissions(tmp_path: Path) -> None:
    path = tmp_path / "station-key.json"
    _, private_key = generate_station_key()
    save_station_key(path, private_key)
    os.chmod(path, 0o644)

    with pytest.raises(IdentityError):
        load_station_key(path)


@pytest.mark.parametrize(
    "content",
    [
        {},
        {"address": ANVIL_ADDRESS_1},
        {"address": ANVIL_ADDRESS_1, "privateKey": "0x" + "11" * 32, "extra": 1},
        {"address": ANVIL_ADDRESS_1, "privateKey": "nie-hex"},
        {"address": ANVIL_ADDRESS_0, "privateKey": "0x" + format(ANVIL_KEY_1, "064x")},
        "nie-obiekt",
    ],
)
def test_load_rejects_inconsistent_key_files(tmp_path: Path, content: object) -> None:
    path = tmp_path / "station-key.json"
    path.write_text(json.dumps(content), encoding="utf-8")
    os.chmod(path, 0o600)

    with pytest.raises(IdentityError):
        load_station_key(path)


def test_load_rejects_missing_files(tmp_path: Path) -> None:
    with pytest.raises(IdentityError):
        load_station_key(tmp_path / "nie-ma.json")
    with pytest.raises(IdentityError):
        load_certificate(tmp_path / "nie-ma.json")


def test_assert_station_ready_accepts_the_shared_certificate() -> None:
    certificate = load_certificate_fixture()
    assert (
        assert_station_ready(
            certificate,
            station_key_file(ANVIL_KEY_1),
            trusted_issuers=[ANVIL_ADDRESS_0],
            now=CERTIFICATE_ISSUED_AT,
        )
        == ANVIL_ADDRESS_1
    )


def test_assert_station_ready_rejects_another_station_key() -> None:
    certificate = load_certificate_fixture()
    with pytest.raises(IdentityError):
        assert_station_ready(
            certificate, station_key_file(ANVIL_KEY_2), now=CERTIFICATE_ISSUED_AT
        )


def test_assert_station_ready_rejects_expired_certificate() -> None:
    certificate = load_certificate_fixture()
    with pytest.raises(IdentityError):
        assert_station_ready(
            certificate, station_key_file(ANVIL_KEY_1), now=CERTIFICATE_EXPIRES_AT
        )


def test_assert_station_ready_rejects_untrusted_issuer() -> None:
    certificate = load_certificate_fixture()
    with pytest.raises(IdentityError):
        assert_station_ready(
            certificate, station_key_file(ANVIL_KEY_1), trusted_issuers=[ANVIL_ADDRESS_1]
        )


def test_cli_generates_a_station_key_without_printing_it(
    tmp_path: Path, capsys: pytest.CaptureFixture[str]
) -> None:
    path = tmp_path / "station-key.json"
    assert main(["--station-key-out", str(path)]) == EXIT_OK

    printed = capsys.readouterr().out
    payload = json.loads(path.read_text(encoding="utf-8"))
    assert payload["address"] in printed
    assert payload["privateKey"] not in printed
    assert stat.S_IMODE(path.stat().st_mode) == 0o600


def test_cli_refuses_to_write_a_key_into_the_repository(
    capsys: pytest.CaptureFixture[str],
) -> None:
    target = REPO_ROOT / "station-key-cli.json"
    assert main(["--station-key-out", str(target)]) == EXIT_FAILURE
    assert "error:" in capsys.readouterr().err
    assert not target.exists()


def issue_arguments(tmp_path: Path, **overrides: object) -> list[str]:
    """Argumenty trybu `--issue-certificate` dla stacji Anvil #1."""
    values: dict[str, object] = {
        "--station-address": ANVIL_ADDRESS_1,
        "--station-id": CERTIFICATE_STATION_REFERENCE,
        "--certificate-id": CERTIFICATE_SERIAL,
        "--verifying-contract": VECTOR_CONTRACT,
        "--chain-id": "31337",
        "--issued-at": str(CERTIFICATE_ISSUED_AT),
        "--allowed-stage-ids": "0,1,2,3",
        "--issuer-key-env": "TEST_ISSUER_KEY",
        "--certificate-out": str(tmp_path / "certificate.json"),
    }
    values.update(overrides)

    arguments = ["--issue-certificate"]
    for flag, value in values.items():
        arguments += [flag, str(value)]
    return arguments


def test_cli_issues_a_certificate(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    monkeypatch.setenv("TEST_ISSUER_KEY", "0x" + format(ANVIL_KEY_0, "064x"))
    assert main(issue_arguments(tmp_path)) == EXIT_OK

    printed = capsys.readouterr().out
    certificate = load_certificate(tmp_path / "certificate.json")
    payload = certificate.payload

    assert payload.stationId == station_id(CERTIFICATE_STATION_REFERENCE)
    assert payload.certificateId == certificate_id(CERTIFICATE_SERIAL)
    assert payload.issuerAddress == ANVIL_ADDRESS_0.lower()
    assert payload.allowedStageIds == [0, 1, 2, 3]
    assert payload.expiresAt == CERTIFICATE_ISSUED_AT + 365 * 24 * 3600
    assert payload.certificateId in printed
    assert format(ANVIL_KEY_0, "064x") not in printed
    assert (
        assert_station_ready(certificate, station_key_file(ANVIL_KEY_1), now=CERTIFICATE_ISSUED_AT)
        == ANVIL_ADDRESS_1
    )


def test_cli_accepts_a_ready_bytes32_identifier(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setenv("TEST_ISSUER_KEY", "0x" + format(ANVIL_KEY_0, "064x"))
    known = station_id("ST-042")

    assert main(issue_arguments(tmp_path, **{"--station-id": known})) == EXIT_OK
    assert load_certificate(tmp_path / "certificate.json").payload.stationId == known


def test_cli_can_write_the_certificate_to_stdout(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    monkeypatch.setenv("TEST_ISSUER_KEY", "0x" + format(ANVIL_KEY_0, "064x"))

    assert main(issue_arguments(tmp_path, **{"--certificate-out": "-"})) == EXIT_OK
    assert '"certificateVersion": 1' in capsys.readouterr().out
    assert not (tmp_path / "certificate.json").exists()


@pytest.mark.parametrize(
    "overrides",
    [{"--station-address": ""}, {"--certificate-id": ""}, {"--verifying-contract": ""}],
)
def test_cli_requires_certificate_options(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
    capsys: pytest.CaptureFixture[str],
    overrides: dict[str, object],
) -> None:
    monkeypatch.setenv("TEST_ISSUER_KEY", "0x" + format(ANVIL_KEY_0, "064x"))

    assert main(issue_arguments(tmp_path, **overrides)) == EXIT_USAGE
    assert "brak wymaganych opcji" in capsys.readouterr().err


@pytest.mark.parametrize(
    "overrides",
    [
        {"--allowed-stage-ids": "0,1,9"},
        {"--allowed-stage-ids": "1,0"},
        {"--chain-id": "0"},
        {"--valid-days": "0"},
    ],
)
def test_cli_rejects_invalid_certificate_fields(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
    capsys: pytest.CaptureFixture[str],
    overrides: dict[str, object],
) -> None:
    monkeypatch.setenv("TEST_ISSUER_KEY", "0x" + format(ANVIL_KEY_0, "064x"))

    assert main(issue_arguments(tmp_path, **overrides)) == EXIT_FAILURE
    assert "error:" in capsys.readouterr().err
    assert not (tmp_path / "certificate.json").exists()


def test_cli_requires_the_issuer_key_in_the_environment(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    monkeypatch.delenv("TEST_ISSUER_KEY", raising=False)

    assert main(issue_arguments(tmp_path)) == EXIT_FAILURE
    assert "TEST_ISSUER_KEY" in capsys.readouterr().err


def test_cli_rejects_the_station_key_as_issuer(
    tmp_path: Path, monkeypatch: pytest.MonkeyPatch, capsys: pytest.CaptureFixture[str]
) -> None:
    monkeypatch.setenv("TEST_ISSUER_KEY", "0x" + format(ANVIL_KEY_0, "064x"))

    arguments = issue_arguments(tmp_path, **{"--station-address": ANVIL_ADDRESS_0})
    assert main(arguments) == EXIT_FAILURE
    assert "nie może być kluczem stacji" in capsys.readouterr().err
