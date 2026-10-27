# Automatyczna weryfikacja

**Język:** YAML pipeline'ów; opcjonalnie krótkie skrypty shell.

Własność CI: osoba 1. Ten katalog opisuje CI i może zawierać skrypty używane przez pipeline.
GitHub Actions wykrywa workflow w `.github/workflows/` względem **korzenia
repozytorium projektu**, nie tutaj. `PZ_supply_blockchain` ma własne repozytorium
Git: workflow umieszczaj w jego `.github/workflows/`, a working-directory ustawiaj
na `contracts`, `backend`, `frontend` lub `simulator` względem tego korzenia.
Przed konfiguracją checkout sprawdź korzeń przez `git rev-parse --show-toplevel`
wykonane wewnątrz projektu; nadrzędny katalog roboczy ma osobne repozytorium.

Po inicjalizacji modułów dodaj niezależne joby:
1. Kontrakty: odtworzenie zależności, format, build, test i kontrola aktualności ABI
   (`forge inspect`); `evm_version` ustawiony jawnie w `foundry.toml`.
2. Backend: JDK 21, Maven Wrapper, `verify`, testowa baza/stuby.
3. Frontend: właściwy Node, `npm ci`, lint, test w trybie jednorazowym, build/type-check.
4. Symulator: Python 3.11-3.12 (venv), odtworzone zależności, pytest.
5. Integracja: Anvil, PostgreSQL, Kubo, deployment i jeden pełny przepływ (w tym test reorg
   przez `evm_snapshot`/`evm_revert`).
6. Dokumentacja: sprawdzenie lokalnych odnośników i walidacja schematów/fixture'ów.

Nie dodawaj pipeline'ów odwołujących się do nieistniejących manifestów.
Runner ma kończyć pracę; watch mode jest zabroniony w CI.
Testy PR używają Kubo/stubów, nie prawdziwej Pinaty ani Amoy (smoke deploy na Amoy: osobny, ręczny krok). Deployment ma osobne
uprawnienia, chronione sekrety i świadome zatwierdzenie środowiska.
