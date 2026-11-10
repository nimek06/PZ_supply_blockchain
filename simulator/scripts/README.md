# Skrypty symulatora

**Język:** shell (bash).

`run-local-e2e.sh` — lokalny przebieg E2E tygodnia 2 (symulator → backend → Anvil),
uruchamiany ręcznie. Dwa tryby:

- `--dry-run` — preflight (Docker, JDK 21, Foundry, venv, pliki klucza/certyfikatu)
  i wypisanie planu scenariusza **bez sieci**; bezpieczne do sprawdzenia środowiska.
- bez `--dry-run` — po sprawdzeniu health backendu i `chainId` Anvila uruchamia
  scenariusz (`202` na każdy krok; `outcome` wymaga workera/relayera z tygodnia 4–5).

Zmienne: `BACKEND_URL`, `RPC_URL`, `CHAIN_ID`, `VERIFYING_CONTRACT`, `STATION_KEY`,
`CERTIFICATE`, `SCENARIO`. Klucz i certyfikat stacji muszą leżeć poza repozytorium
(albo w `infrastructure/local/secrets/`); skrypt nie wypisuje klucza.

Stos (Compose, deploy kontraktu, rejestracja stacji, backend) stawiamy ręcznie —
skrypt wypisuje kolejność. To celowe: nie uruchamiamy cudzych usług w tle.
