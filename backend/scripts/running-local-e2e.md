# Uruchamianie lokalnej symulacji E2E

Krótka instrukcja uruchomienia pełnej ścieżki:
**Compose (Postgres/Anvil/IPFS) → kontrakty → backend → symulator**.

## Wymagania

- Docker + `docker compose`
- JDK 21
- Python 3.11+ (do venv symulatora)
- `jq`, `shasum`
- `forge`/`cast` **nie są wymagane** — używamy obrazu Foundry

## Szybki start

```bash
cd <ścieżka-do-repo>
# wystarczy raz przygotować .env w infrastructure/local/
cp infrastructure/local/.env.example infrastructure/local/.env
# edytuj infrastructure/local/.env (hasło, port Postgres)
./backend/scripts/local-e2e/run-e2e.sh
```

Po zakończeniu zobaczysz:

```text
scenariusz ebike-happy-path: partia 0x7970... (2 kroki)
  etap 0: qaPassed=True status=ACCEPTED outcome=None nextStage=0
  etap 1: qaPassed=True status=ACCEPTED outcome=None nextStage=0
```

`outcome=None` jest oczekiwane — worker/relayer jest poza zakresem tygodnia 2.

## Konfiguracja

Skrypty E2E czytają `infrastructure/local/.env` (jeśli istnieje). Dzięki temu używają tych samych wartości co `docker compose` — wystarczy ustawić `.env` raz.

Przykład:

```bash
cp infrastructure/local/.env.example infrastructure/local/.env
# edytuj infrastructure/local/.env:
#   POSTGRES_PASSWORD=test
#   POSTGRES_PORT=54432   # gdy 5432 zajęty przez systemowy Postgres
./backend/scripts/local-e2e/run-e2e.sh
```

## Zmienne środowiskowe

Możesz też nadpisać wartości ad-hoc (mają priorytet nad `.env`):

| Zmienna | Domyślnie | Opis |
|---|---|---|
| `E2E_WORKDIR` | `backend/scripts/tmp/e2e-run-<timestamp>` | katalog na klucz, certyfikat i logi |
| `POSTGRES_PORT` | `5432` | port Postgres |
| `POSTGRES_PASSWORD` | `supplychain` | hasło użytkownika Postgres |
| `BACKEND_URL` | `http://127.0.0.1:8080` | adres backendu |
| `RPC_URL` | `http://127.0.0.1:8545` | adres Anvila |
| `CANONICAL_CONTRACT` | `0x5FbDB2315678afecb367f032d93F642f64180aa3` | adres, którego E2E oczekuje po resecie Anvila |

```bash
POSTGRES_PORT=54432 ./backend/scripts/local-e2e/run-e2e.sh
```

## Krok po kroku

Skrypt `run-e2e.sh` wykonuje kolejno:

1. `setup-stack.sh` — startuje Postgres, Anvil i Kubo, resetuje Anvil do pustego łańcucha, ustawia hasło Postgres i tworzy bazy `supplychain_e2e` oraz `supplychain_e2e_test`.
2. `deploy-contracts.sh` — `deploy-local.sh --docker` (wdraża SupplyChain i role pod kanonicznym adresem `0x5FbDB2315678afecb367f032d93F642f64180aa3`).
3. `start-backend.sh` — buduje jar i uruchamia backend w tle, czeka na `/actuator/health` i migracje.
4. `prepare-station.sh` — klucz stacji, certyfikat z aktualnym `issuedAt`, rejestracja on-chain, zapis certyfikatu do bazy.
5. `run-simulator.sh` — scenariusz `ebike-happy-path.yaml`.

Każdy krok można uruchomić osobno:

```bash
./backend/scripts/local-e2e/setup-stack.sh
./backend/scripts/local-e2e/deploy-contracts.sh
./backend/scripts/local-e2e/prepare-station.sh
./backend/scripts/local-e2e/start-backend.sh
./backend/scripts/local-e2e/run-simulator.sh
```

## Pliki tymczasowe

Domyślnie skrypt tworzy katalog w `backend/scripts/tmp/e2e-run-<timestamp>` i tam zapisuje:

- klucz stacji (`station.key`, uprawnienia `0600`)
- certyfikat stacji (`station-certificate.json`)
- log backendu (`backend.log`)

Każde uruchomienie `run-e2e.sh` ma swój unikalny katalog. Gdy uruchamiasz kroki osobno, domyślny katalog to `backend/scripts/tmp/e2e-run-<timestamp>`.

Katalog `backend/scripts/tmp/` jest w `.gitignore` — nie trafi do repozytorium.

Po zakończeniu logi zostają — możesz je usunąć ręcznie:

```bash
rm -rf backend/scripts/tmp/e2e-run-*
```

Gdy uruchamiasz kroki osobno, backend zatrzymasz przez:

```bash
kill "$(cat backend/scripts/tmp/e2e-run-*/backend.pid)"
```

## Co to potwierdza

- Partia → etap 0 → etap 1 → `202` dla obu etapów.
- Weryfikacja certyfikatu stacji (T010) działa.
- Walidacja profilu e-bike jest egzekwowana.
- Brak `CONFIRMED`/`outcome` — to oczekiwane w tygodniu 2 (worker/relayer poza zakresem).

## Najczęstsze problemy

| Problem | Rozwiązanie |
|---|---|
| `password authentication failed` | Zresetuj hasło: `docker compose -f infrastructure/local/compose.yaml exec postgres psql -U supplychain -d supplychain -c "ALTER USER supplychain WITH PASSWORD 'supplychain';"` |
| `Port 8080 was already in use` | `fuser -k 8080/tcp` i uruchom ponownie |
| `CERTIFICATE_INVALID` | Sprawdź, czy certyfikat został wstawiony do bazy przez `prepare-station.sh` |
| `STATION_NOT_ACTIVE` | Ponownie uruchom `deploy-contracts.sh` i `prepare-station.sh` |
| `REGISTRY_MISMATCH` | `prepare-station.sh` nie zarejestrował stacji z `expiresAt` z certyfikatu — uruchom go ponownie |
| brak `forge`/`cast` | Upewnij się, że uruchamiasz `deploy-contracts.sh`, który używa `--docker` |
| kontrakt pod innym adresie niż `0x5FbDB2315678afecb367f032d93F642f64180aa3` | Upewnij się, że nie uruchomiłeś wcześniej `docker compose up -d` (serwis `deploy` zużywa nonce). `setup-stack.sh` resetuje Anvil, więc ponowne uruchomienie `run-e2e.sh` przywraca kanoniczny adres. |
