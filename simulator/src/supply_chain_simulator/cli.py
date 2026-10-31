"""Punkt wejścia CLI symulatora.

T006 to szkielet: CLI obsługuje ``--help`` i ``--version``. Opcje trybu,
scenariusza, adresu backendu, sieci/kontraktu, seeda i konfiguracji stacji
dochodzą w kolejnych zadaniach razem z modelem raportu i klientem API.
Uruchomienie bez argumentów jest błędem użycia (kod 2), a nie cichym
"poprawnym" zakończeniem.
"""

from __future__ import annotations

import argparse
import sys
from collections.abc import Sequence

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
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    """Uruchamia CLI i zwraca kod wyjścia.

    ``argv`` służy do wstrzykiwania argumentów w testach; ``None`` oznacza
    ``sys.argv[1:]``. ``--help`` i ``--version`` kończy argparse przez
    ``SystemExit(0)``. ``EXIT_OK`` będzie zwracane po zaimplementowaniu trybów.
    """
    parser = build_parser()
    args = parser.parse_args(argv)

    if args.export_signature_vector is not None:
        # Import lokalny: pakiet nie ładuje eth-account przy samym imporcie.
        from pathlib import Path

        from supply_chain_simulator.vector import write_signature_vector

        target = Path(args.export_signature_vector) if args.export_signature_vector else None
        try:
            path = write_signature_vector(target)
        except OSError as exc:
            print(f"error: nie udało się zapisać wektora: {exc}", file=sys.stderr)
            return EXIT_FAILURE
        print(f"zapisano wektor: {path}")
        return EXIT_OK

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
