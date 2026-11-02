# CI na GitHub Actions

**Język:** YAML. Właściciel od tygodnia 2: osoba 1. Szkielet przygotowała osoba 4 (T010).

## Pliki

- [workflows/ci.yml](workflows/ci.yml): joby `contracts` (Foundry), `simulator` (pytest), `backend` (Maven).

## Co robi każdy job

| Job | Kroki | Uwagi |
|---|---|---|
| `contracts` | checkout z submodułami, instalacja Foundry, `forge fmt --check`, `forge build`, `forge test -vv` | Submoduły `contracts/lib/openzeppelin-contracts` i `contracts/lib/forge-std` muszą być pobrane; testy czytają wektor z `../shared/fixtures` (uprawnienia `fs_permissions` w `contracts/foundry.toml`). |
| `simulator` | Python 3.12, `pip install -e ".[dev]"`, smoke CLI, `python -m pytest -q` | Testy nie używają sieci ani sekretów, więc nie potrzebują Anvila ani Pinaty. |
| `backend` | Java 21 (temurin) i `./mvnw -B -ntp verify` | Do inicjalizacji backendu warunek `hashFiles('backend/pom.xml')` pomija kroki i wypisuje `::notice`; job jest wtedy zielony bez udawania testów. Po pojawieniu się `pom.xml` i `mvnw` warunki można usunąć. |

## Zasady

- Zakres testów wynika z [macierzy testów](../docs/testing.md), a pełną listę
  jobów docelowych opisuje [infrastructure/ci/README.md](../infrastructure/ci/README.md)
  (własność CI: osoba 1). CI **nie** wymaga dostępu do publicznej sieci Amoy ani
  do Pinaty - integracja pionowa na Amoy i prawdziwej Pinacie pozostaje ręczna
  (tydzień 5).
- Sekrety: w CI nie ma kluczy ani tokenów. Testowy klucz odtwarza kod testu z
  jawnie oznaczonych, publicznych kluczy deweloperskich; JWT Pinaty i klucze
  produkcyjne zostają w `.env` poza repozytorium.
- Akcje są przypięte do znanych wersji major (`actions/checkout@v4`,
  `actions/setup-python@v5`, `actions/setup-java@v4`,
  `foundry-rs/foundry-toolchain@v1`). Podniesienie wersji rób świadomie i razem
  ze zmianą tego pliku, żeby wszyscy mieli ten sam toolchain.
- Lokalnie te same komendy: `forge test` w `contracts/`, `python -m pytest` w
  `simulator/`, `./mvnw verify` w `backend/`.

## Czego w szkielecie jeszcze nie ma (dla osoby 1)

Szkielet pokrywa trzy joby z listy w
[infrastructure/ci/README.md](../infrastructure/ci/README.md) (kontrakty, backend,
symulator). Do dopisania od tygodnia 2:

1. Kontrola aktualności ABI: `forge inspect SupplyChain abi` i porównanie z
   `shared/abi/SupplyChain.abi.json` (plus skrypt w `infrastructure/ci/`, żeby
   komenda była jedna dla CI i dla człowieka).
2. Job frontendu: właściwy Node z `frontend/package.json`, `npm ci`, lint, test
   jednorazowy, build/type-check (właściciel: osoba 3 od tygodnia 2).
3. Job integracyjny: Anvil, PostgreSQL i Kubo (testy backendu z Testcontainers
   wymagają Dockera), jeden pełny przepływ i test reorg przez
   `evm_snapshot`/`evm_revert`. Na razie świadomie pominięty - nie ma jeszcze
   backendu ani Compose.
4. Job dokumentacji: sprawdzenie lokalnych odnośników i walidacja schematów oraz
   fixture'ów (`jsonschema` z extra `dev` symulatora).

Po pojawieniu się `backend/pom.xml` i `backend/mvnw` usuń warunki
`hashFiles('backend/pom.xml')` z jobu `backend` - wtedy brak manifestu ma już
kończyć się czerwonym jobem, a nie komunikatem.

