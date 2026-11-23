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
