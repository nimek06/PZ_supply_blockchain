"""Punkt wejścia CLI symulatora.

CLI obsługuje ``--help``, ``--version``, ``--export-signature-vector``,
``--export-certificate-fixture``, ``--station-key-out`` (generowanie klucza
stacji) i ``--issue-certificate`` (wystawienie certyfikatu kluczem wystawcy
z ``--issuer-key-env``). Opcje trybu, scenariusza, adresu backendu, seeda
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


def main(argv: Sequence[str] | None = None) -> int:
    """Uruchamia CLI i zwraca kod wyjścia.

    ``argv`` służy do wstrzykiwania argumentów w testach; ``None`` oznacza
    ``sys.argv[1:]``. ``--help`` i ``--version`` kończy argparse przez
    ``SystemExit(0)``. ``EXIT_OK`` będzie zwracane po zaimplementowaniu trybów.
    """
    parser = build_parser()
    args = parser.parse_args(argv)

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
