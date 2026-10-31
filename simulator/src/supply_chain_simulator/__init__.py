"""Symulator stacji montażowych: raporty, podpisy EIP-712 i klient API.

Pakiet nie wykonuje przy imporcie żadnych operacji sieciowych ani odczytu
plików z sekretami. Konfigurację wczytuje CLI (``cli.py``) i przekazuje
do klienta oraz signera.
"""

from __future__ import annotations

__all__ = ["__version__"]

__version__ = "0.1.0"
