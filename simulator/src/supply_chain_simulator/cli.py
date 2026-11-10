"""Punkt wejścia CLI symulatora.

CLI obsługuje ``--help``, ``--version``, ``--export-signature-vector``,
``--export-certificate-fixture``, ``--export-negative-fixtures``
(negatywne wektory profilu i zgodny negatyw), ``--export-valid-fixtures``
(poprawne raporty etapów 0-3), ``--export-certificate-negatives``
(wygasły i niezaufany wystawca), ``--station-key-out`` (generowanie
klucza stacji) i ``--issue-certificate`` (wystawienie certyfikatu kluczem
wystawcy z ``--issuer-key-env``). Opcje trybu, scenariusza, adresu backendu, seeda
i klienta API dochodzą w kolejnych zadaniach. Uruchomienie bez argumentów jest
błędem użycia (kod 2), a nie cichym "poprawnym" zakończeniem.

Klucza prywatnego nie wypisujemy nigdy: logi podają tylko adresy i ścieżki.
"""

from __future__ import annotations

import argparse
import json
import os
import sys
import time
from collections.abc import Callable, Sequence

from supply_chain_simulator import __version__

EXIT_OK = 0
EXIT_FAILURE = 1
EXIT_USAGE = 2


def build_parser() -> argparse.ArgumentParser:
    """Zwraca parser argumentów CLI bez efektów ubocznych."""
    parser = argparse.ArgumentParser(
        prog="supply-chain-simulator",
        description=(
            "Symulator stacji montażowych: generowanie raportów, podpisy "
            "EIP-712 StageCompletion i klient API backendu."
        ),
    )
    parser.add_argument(
        "--version",
        action="version",
        version=f"%(prog)s {__version__}",
        help="wypisz wersję i zakończ",
    )
    parser.add_argument(
        "--export-signature-vector",
        nargs="?",
        const="",
        default=None,
        metavar="PATH",
        help=(
            "zapisz wspólny wektor podpisu "
            "(domyślnie shared/fixtures/signature-vector.json) i zakończ"
        ),
    )
    parser.add_argument(
        "--export-certificate-fixture",
        nargs="?",
        const="",
        default=None,
        metavar="PATH",
        help=(
            "zapisz przykładowy certyfikat stacji z podpisem wystawcy "
            "(domyślnie shared/fixtures/station-certificate.json) i zakończ"
        ),
    )
    parser.add_argument(
        "--export-negative-fixtures",
        nargs="?",
        const="",
        default=None,
        metavar="DIR",
        help=(
            "zapisz negatywne fixture'y (naruszenia profilu etapów 0-3 + report-qa-fail.json) "
            "(domyślnie katalog shared/fixtures) i zakończ"
        ),
    )
    parser.add_argument(
        "--export-valid-fixtures",
        nargs="?",
        const="",
        default=None,
        metavar="DIR",
        help=(
            "zapisz poprawne fixture'y etapów 0-3 (report-valid-stageN.json) "
            "(domyślnie katalog shared/fixtures) i zakończ"
        ),
    )
    parser.add_argument(
        "--export-certificate-negatives",
        nargs="?",
        const="",
        default=None,
        metavar="DIR",
        help=(
            "zapisz negatywne warianty certyfikatu (wygasły, niezaufany wystawca) "
            "(domyślnie katalog shared/fixtures) i zakończ"
        ),
    )
    parser.add_argument(
        "--station-key-out",
        metavar="PATH",
        help=(
            "wygeneruj klucz stacji, zapisz go w pliku 0600 poza repozytorium "
            "(albo w infrastructure/local/secrets/) i zakończ"
        ),
    )
    parser.add_argument(
        "--issue-certificate",
        action="store_true",
        help="wystaw certyfikat stacji kluczem wystawcy z --issuer-key-env i zakończ",
    )
    parser.add_argument("--station-address", metavar="ADDRESS", help="adres EVM stacji")
    parser.add_argument(
        "--station-id",
        metavar="TEXT|0x...",
        help="identyfikator stacji: tekst (keccak256) albo gotowy bytes32",
    )
    parser.add_argument(
        "--certificate-id",
        metavar="TEXT|0x...",
        help="serial certyfikatu: tekst (keccak256) albo gotowy bytes32",
    )
    parser.add_argument(
        "--chain-id", type=int, default=31337, metavar="INT", help="chainId certyfikatu"
    )
    parser.add_argument(
        "--verifying-contract", metavar="ADDRESS", help="adres wdrożonego SupplyChain"
    )
    parser.add_argument(
        "--allowed-stage-ids",
        default="0,1,2,3",
        metavar="LIST",
        help="dozwolone etapy, np. 0,1,2,3 (domyślnie wszystkie)",
    )
    parser.add_argument(
        "--issued-at", type=int, metavar="UNIX", help="początek ważności (domyślnie teraz)"
    )
    parser.add_argument(
        "--valid-days", type=int, default=365, metavar="DAYS", help="ważność certyfikatu w dniach"
    )
    parser.add_argument(
        "--issuer-key-env",
        default="ADMIN_PRIVATE_KEY",
        metavar="NAME",
        help="zmienna środowiskowa z kluczem wystawcy (wartości nie wypisujemy)",
    )
    parser.add_argument(
        "--certificate-out",
        metavar="PATH",
        help=(
            "plik certyfikatu (domyślnie infrastructure/local/secrets/"
            "station-certificate-<adres>.json); `-` wypisuje na stdout"
        ),
    )
    parser.add_argument(
        "--scenario", metavar="PATH", help="scenariusz YAML do uruchomienia (tryb run)"
    )
    parser.add_argument(
        "--backend-url", metavar="URL", help="adres backendu, np. http://127.0.0.1:8080"
    )
    parser.add_argument(
        "--rpc-url", metavar="URL", help="adres RPC, np. http://127.0.0.1:8545"
    )
    parser.add_argument(
        "--station-key", metavar="PATH", help="plik klucza stacji (0600, poza repozytorium)"
    )
    parser.add_argument("--certificate", metavar="PATH", help="plik certyfikatu stacji (JSON)")
    parser.add_argument(
        "--seed", type=int, metavar="INT", help="seed pomiarów (domyślnie z scenariusza)"
    )
    parser.add_argument(
        "--timeout", type=float, default=10.0, metavar="SECONDS", help="timeout HTTP/RPC"
    )
    parser.add_argument(
        "--token-env", metavar="NAME", help="zmienna środowiskowa z tokenem Bearer (nie wypisywany)"
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="tryb --scenario: sprawdź konfigurację, klucz, certyfikat i plan, bez sieci",
    )
    return parser


def _as_bytes32(value: str, derive: Callable[[str], str]) -> str:
    """Przyjmuje gotowy `0x` + 64 hex albo tekst, który zamieniamy na keccak256."""
    text = value.strip()
    if text.startswith("0x") and len(text) == 66:
        return text.lower()
    return derive(text)


def _parse_stage_ids(value: str) -> list[int]:
    """Zamienia `0,1,2,3` na numery etapów; zakres i kolejność sprawdza model."""
    parts = [part.strip() for part in value.split(",") if part.strip()]
    if not parts:
        raise ValueError("lista etapów nie może być pusta")
    try:
        return [int(part) for part in parts]
    except ValueError as exc:
        raise ValueError(f"etapy muszą być liczbami całkowitymi: {value!r}") from exc


def _station_key_mode(args: argparse.Namespace) -> int:
    """Generuje klucz stacji i zapisuje go poza repozytorium (0600)."""
    from pathlib import Path

    from supply_chain_simulator.identity import (
        IdentityError,
        generate_station_key,
        save_station_key,
    )

    _, private_key = generate_station_key()
    try:
        key_file = save_station_key(Path(args.station_key_out), private_key)
    except IdentityError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return EXIT_FAILURE

    # Klucz prywatny nigdy nie trafia na wyjście - podajemy tylko adres i ścieżkę.
    print(f"zapisano klucz stacji dla adresu {key_file.address} ({key_file.path})")
    return EXIT_OK


def _certificate_mode(args: argparse.Namespace) -> int:
    """Wystawia certyfikat stacji kluczem wystawcy z `--issuer-key-env`."""
    from pathlib import Path

    from supply_chain_simulator.certificate import certificate_id, sign_certificate, station_id
    from supply_chain_simulator.identity import (
        IdentityError,
        address_for_key,
        secrets_directory,
    )
    from supply_chain_simulator.models import CertificatePayload

    required = ("station_address", "station_id", "certificate_id", "verifying_contract")
    missing = [name for name in required if not getattr(args, name)]
    if missing:
        flags = ", ".join("--" + name.replace("_", "-") for name in missing)
        print(f"error: brak wymaganych opcji: {flags}", file=sys.stderr)
        return EXIT_USAGE

    issuer_key_hex = os.environ.get(args.issuer_key_env, "").strip()
    if not issuer_key_hex:
        print(
            f"error: brak klucza wystawcy w zmiennej {args.issuer_key_env} "
            "(sekrety trzymaj w .env poza Git)",
            file=sys.stderr,
        )
        return EXIT_FAILURE
    try:
        issuer_key = int(issuer_key_hex, 16)
    except ValueError:
        print(f"error: {args.issuer_key_env} nie jest kluczem hex", file=sys.stderr)
        return EXIT_FAILURE

    issued_at = args.issued_at if args.issued_at is not None else int(time.time())
    try:
        payload = CertificatePayload(
            schemaVersion=1,
            certificateId=_as_bytes32(args.certificate_id, certificate_id),
            stationId=_as_bytes32(args.station_id, station_id),
            stationAddress=args.station_address,
            issuerAddress=address_for_key(issuer_key),
            chainId=args.chain_id,
            verifyingContract=args.verifying_contract,
            issuedAt=issued_at,
            expiresAt=issued_at + args.valid_days * 24 * 3600,
            allowedStageIds=_parse_stage_ids(args.allowed_stage_ids),
        )
    except (ValueError, IdentityError) as exc:
        print(f"error: niepoprawny certyfikat: {exc}", file=sys.stderr)
        return EXIT_FAILURE

    if payload.stationAddress == payload.issuerAddress:
        print(
            "error: klucz wystawcy nie może być kluczem stacji "
            "(administrator i stacja to osobne konta)",
            file=sys.stderr,
        )
        return EXIT_FAILURE

    certificate, _ = sign_certificate(payload, issuer_key)
    text = json.dumps(certificate.model_dump(mode="json"), indent=2, ensure_ascii=False) + "\n"

    if args.certificate_out == "-":
        print(text, end="")
        return EXIT_OK

    target = (
        Path(args.certificate_out)
        if args.certificate_out
        else secrets_directory() / f"station-certificate-{payload.stationAddress}.json"
    )
    try:
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text, encoding="utf-8")
    except OSError as exc:
        print(f"error: nie udało się zapisać certyfikatu: {exc}", file=sys.stderr)
        return EXIT_FAILURE

    print(
        f"zapisano certyfikat {payload.certificateId} dla {payload.stationAddress} ({target})"
    )
    return EXIT_OK


def _run_mode(args: argparse.Namespace) -> int:
    """Uruchamia scenariusz: konfiguracja, klucz/certyfikat stacji, runner, raport."""
    from pathlib import Path

    from supply_chain_simulator.chain import ChainReader
    from supply_chain_simulator.client import ApiError, SimulatorApiClient
    from supply_chain_simulator.config import ConfigError, SimulatorConfig
    from supply_chain_simulator.identity import (
        IdentityError,
        assert_station_ready,
        load_certificate,
        load_station_key,
    )
    from supply_chain_simulator.runner import Runner, RunnerError
    from supply_chain_simulator.scenario import ScenarioError, load_scenario

    required = [
        ("--station-key", args.station_key),
        ("--certificate", args.certificate),
        ("--verifying-contract", args.verifying_contract),
    ]
    if not args.dry_run:
        required += [
            ("--backend-url", args.backend_url),
            ("--rpc-url", args.rpc_url),
        ]
    missing = [name for name, value in required if not value]
    if missing:
        print(
            f"error: tryb --scenario wymaga argumentów: {', '.join(missing)}",
            file=sys.stderr,
        )
        return EXIT_USAGE

    token: str | None = None
    if args.token_env:
        token = os.environ.get(args.token_env)
        if not token:
            print(f"error: zmienna {args.token_env} jest pusta albo nieustawiona", file=sys.stderr)
            return EXIT_FAILURE

    try:
        scenario = load_scenario(args.scenario)
        station_key = load_station_key(Path(args.station_key))
        certificate = load_certificate(Path(args.certificate))
        assert_station_ready(certificate, station_key)
    except (ScenarioError, IdentityError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return EXIT_FAILURE

    if args.dry_run:
        print(
            f"[dry-run] scenariusz {scenario.name}: partia {scenario.batchNumber} "
            f"-> batchId {scenario.batch_id()}"
        )
        print(f"  stacja {scenario.station.reference}: {station_key.address} (klucz i certyfikat OK)")
        print(f"  chainId {args.chain_id}, verifyingContract {args.verifying_contract}")
        for step in scenario.steps:
            mode = "qa-fail" if step.forceQaFail else "qa-pass"
            outcome = f", outcome {step.expect.outcome}" if step.expect.outcome else ""
            print(f"  etap {step.stageId}: {mode} -> HTTP {step.expect.httpStatus}{outcome}")
        return EXIT_OK

    try:
        config = SimulatorConfig(
            backend_url=args.backend_url,
            rpc_url=args.rpc_url,
            chain_id=args.chain_id,
            verifying_contract=args.verifying_contract,
            station_key_path=Path(args.station_key),
            certificate_path=Path(args.certificate),
            seed=args.seed if args.seed is not None else scenario.seed,
            timeout=args.timeout,
            token=token,
        )
    except ConfigError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return EXIT_FAILURE

    api = SimulatorApiClient(config.backend_url, token=config.token, timeout=config.timeout)
    chain = ChainReader(config.rpc_url, config.verifying_contract, timeout=config.timeout)
    try:
        result = Runner(
            config=config,
            scenario=scenario,
            station_key=station_key,
            certificate=certificate,
            api=api,
            chain=chain,
        ).run()
    except (RunnerError, ApiError) as exc:
        print(f"error: przebieg nie powiódł się: {exc}", file=sys.stderr)
        return EXIT_FAILURE
    finally:
        api.close()
        chain.close()

    print(f"scenariusz {result.scenario}: partia {result.batch_id} ({len(result.steps)} kroki)")
    for step in result.steps:
        print(
            f"  etap {step.stage_id}: qaPassed={step.qa_passed} status={step.status} "
            f"outcome={step.outcome} nextStage={step.next_stage}"
        )
    return EXIT_OK


def main(argv: Sequence[str] | None = None) -> int:
    """Uruchamia CLI i zwraca kod wyjścia.

    ``argv`` służy do wstrzykiwania argumentów w testach; ``None`` oznacza
    ``sys.argv[1:]``. ``--help`` i ``--version`` kończy argparse przez
    ``SystemExit(0)``. ``EXIT_OK`` będzie zwracane po zaimplementowaniu trybów.
    """
    parser = build_parser()
    args = parser.parse_args(argv)

    if args.export_negative_fixtures is not None:
        # Import lokalny: pakiet nie ładuje eth-account przy samym imporcie.
        from pathlib import Path

        from supply_chain_simulator.vector import write_negative_fixtures

        directory = Path(args.export_negative_fixtures) if args.export_negative_fixtures else None
        try:
            paths = write_negative_fixtures(directory)
        except OSError as exc:
            print(f"error: nie udało się zapisać fixturów negatywnych: {exc}", file=sys.stderr)
            return EXIT_FAILURE
        for path in paths:
            print(f"zapisano wektor negatywny: {path}")
        return EXIT_OK

    if args.export_valid_fixtures is not None:
        from pathlib import Path

        from supply_chain_simulator.vector import write_valid_fixtures

        directory = Path(args.export_valid_fixtures) if args.export_valid_fixtures else None
        try:
            paths = write_valid_fixtures(directory)
        except OSError as exc:
            print(f"error: nie udało się zapisać poprawnych fixturów: {exc}", file=sys.stderr)
            return EXIT_FAILURE
        for path in paths:
            print(f"zapisano poprawny fixture: {path}")
        return EXIT_OK

    if args.export_certificate_negatives is not None:
        # Import lokalny: pakiet nie ładuje eth-account przy samym imporcie.
        from pathlib import Path

        from supply_chain_simulator.vector import write_certificate_negative_fixtures

        directory = (
            Path(args.export_certificate_negatives)
            if args.export_certificate_negatives
            else None
        )
        try:
            paths = write_certificate_negative_fixtures(directory)
        except OSError as exc:
            print(f"error: nie udało się zapisać wariantów certyfikatu: {exc}", file=sys.stderr)
            return EXIT_FAILURE
        for path in paths:
            print(f"zapisano certyfikat negatywny: {path}")
        return EXIT_OK

    if args.export_signature_vector is not None or args.export_certificate_fixture is not None:
        # Import lokalny: pakiet nie ładuje eth-account przy samym imporcie.
        from pathlib import Path

        from supply_chain_simulator.vector import (
            write_certificate_fixture,
            write_signature_vector,
        )

        if args.export_signature_vector is not None:
            raw, writer, label = args.export_signature_vector, write_signature_vector, "wektor"
        else:
            raw, writer, label = (
                args.export_certificate_fixture,
                write_certificate_fixture,
                "certyfikat",
            )

        target = Path(raw) if raw else None
        try:
            path = writer(target)
        except OSError as exc:
            print(f"error: nie udało się zapisać pliku ({label}): {exc}", file=sys.stderr)
            return EXIT_FAILURE
        print(f"zapisano {label}: {path}")
        return EXIT_OK

    if args.scenario is not None:
        return _run_mode(args)
    if args.station_key_out is not None:
        return _station_key_mode(args)
    if args.issue_certificate:
        return _certificate_mode(args)

    # Tryb pracy nie jest jeszcze zaimplementowany: brak argumentów to błąd użycia.
    parser.print_help(sys.stderr)
    print(
        "error: brak trybu pracy - opcje --mode/--scenario/--backend-url "
        "pojawią się w kolejnych zadaniach (T009+).",
        file=sys.stderr,
    )
    return EXIT_USAGE


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
