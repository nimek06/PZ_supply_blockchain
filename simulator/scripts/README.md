# Skrypty symulatora

**Język:** shell (bash).

**Kanoniczne uruchomienie lokalne** opisuje `backend/scripts/running-local-e2e.md`
(`backend/scripts/local-e2e/run-e2e.sh`, tydzień 3): jednym poleceniem stawia
PostgreSQL/Anvil/Kubo, wdraża kontrakty, uruchamia backend, wystawia i rejestruje
stację (certyfikat wstawia do bazy) i woła scenariusz symulatora. Skrypty w tym
katalogu są lżejszą alternatywą, gdy stos stoi już ręcznie.

`run-local-e2e.sh` — przebieg symulator → backend → Anvil, uruchamiany ręcznie. Dwa tryby:

- `--dry-run` — preflight (Docker, JDK 21, Foundry, venv, pliki klucza/certyfikatu)
  i wypisanie planu scenariusza **bez sieci**; bezpieczne do sprawdzenia środowiska.
- bez `--dry-run` — po sprawdzeniu health backendu i `chainId` Anvila uruchamia
  scenariusz (`202` na każdy krok; `outcome` wymaga włączonego workera/relayera).

Zmienne: `BACKEND_URL`, `RPC_URL`, `CHAIN_ID`, `VERIFYING_CONTRACT`, `STATION_KEY`,
`CERTIFICATE`, `SCENARIO`. Klucz i certyfikat stacji muszą leżeć poza repozytorium
(albo w `infrastructure/local/secrets/`); skrypt nie wypisuje klucza.

Stos (Compose, deploy kontraktu, rejestracja stacji, backend) stawiamy ręcznie —
skrypt wypisuje kolejność. To celowe: nie uruchamiamy cudzych usług w tle.

## Pion partia → etap 0 (tydzień 4)

- `create-batch.sh` — **operator** tworzy partię: SIWE (`POST /auth/challenges` →
  `cast wallet sign` → `POST /auth/sessions`) i `POST /batches` z `Idempotency-Key`.
  Na stdout wypisuje numer partii `B-RRRR-NNNN`, szczegóły na stderr. Klucz domyślny
  to publiczny klucz deweloperski Anvil #3 (rola `OPERATOR_ROLE`); nadpisz przez
  `OPERATOR_PRIVATE_KEY` lub `OPERATOR_KEY_ENV`. Symulator pozostaje stacją i nie
  tworzy partii (`docs/rules/access-control.md`: „Osoba 4: login skryptowy i testy”).
- `run-vertical.sh` — pion jedną komendą: `create-batch.sh`, a potem scenariusz
  `ebike-vertical.yaml` z `--batch-number` i czekaniem na `nextStage=1`. Wymaga
  backendu z **włączonym workerem/relayerem** (`RELAYER_WORKER_ENABLED=true`,
  `RELAYER_PRIVATE_KEY=<klucz Anvil #3>`) i indexerem; bez workera użyj
  `run-local-e2e.sh` (accept-only).

Zmienne: `BACKEND_URL`, `RPC_URL`, `CHAIN_ID`, `VERIFYING_CONTRACT`, `STATION_KEY`,
`CERTIFICATE`, `SCENARIO`, a dla `create-batch.sh`: `OPERATOR_PRIVATE_KEY` /
`OPERATOR_KEY_ENV` i opcjonalnie `IDEMPOTENCY_KEY`. Klucze zostają poza repozytorium;
skrypty nie wypisują kluczy.
