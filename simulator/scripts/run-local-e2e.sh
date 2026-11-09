#!/usr/bin/env bash
# Lokalny przebieg E2E symulatora (tydzień 2): scenariusz e-bike na lokalnym
# backendzie i Anvilu do `202`. Uruchamiany ręcznie; nie stawia stosu za Ciebie.
#
# Wymagania: Docker + `docker compose` (PostgreSQL/Anvil/Kubo), JDK 21
# (backend), Foundry (`forge`/`cast`), venv symulatora oraz klucz i certyfikat
# stacji poza repozytorium. Bez `outcome` - worker i relayer są poza tygodniem 2,
# więc oczekujemy `202`, nie `CONFIRMED`.
#
# Użycie:
#   STATION_KEY=... CERTIFICATE=... VERIFYING_CONTRACT=0x... \
#     scripts/run-local-e2e.sh --dry-run      # preflight + plan, bez sieci
#   STATION_KEY=... CERTIFICATE=... VERIFYING_CONTRACT=0x... \
#     scripts/run-local-e2e.sh                # realny przebieg (backend+Anvil w górze)
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SIMULATOR_DIR="$REPO_ROOT/simulator"
COMPOSE_DIR="$REPO_ROOT/infrastructure/local"

BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:8080}"
RPC_URL="${RPC_URL:-http://127.0.0.1:8545}"
CHAIN_ID="${CHAIN_ID:-31337}"
VERIFYING_CONTRACT="${VERIFYING_CONTRACT:-}"
STATION_KEY="${STATION_KEY:-}"
CERTIFICATE="${CERTIFICATE:-}"
SCENARIO="${SCENARIO:-scenarios/ebike-happy-path.yaml}"

DRY_RUN=0
if [ "${1:-}" = "--dry-run" ]; then
  DRY_RUN=1
fi

fail() {
  echo "error: $*" >&2
  exit 1
}

simulator() {
  ( cd "$SIMULATOR_DIR" && .venv/bin/python -m supply_chain_simulator "$@" )
}

preflight() {
  command -v docker >/dev/null 2>&1 || fail "brak Dockera (PostgreSQL/Anvil/Kubo w Compose)"
  docker compose version >/dev/null 2>&1 || fail "brak wtyczki 'docker compose'"
  command -v java >/dev/null 2>&1 || fail "brak Javy; backend wymaga JDK 21"
  java -version 2>&1 | grep -q '"21' || fail "backend wymaga JDK 21 (jest: $(java -version 2>&1 | head -1))"
  command -v forge >/dev/null 2>&1 || fail "brak Foundry (forge/cast)"
  [ -d "$COMPOSE_DIR" ] || fail "brak $COMPOSE_DIR (Compose Osoby 3)"
  [ -x "$SIMULATOR_DIR/.venv/bin/python" ] || fail "brak venv symulatora ($SIMULATOR_DIR/.venv)"
  [ -n "$VERIFYING_CONTRACT" ] || fail "ustaw VERIFYING_CONTRACT (adres z shared/deployments/)"
  [ -n "$STATION_KEY" ] || fail "ustaw STATION_KEY (plik klucza stacji 0600)"
  [ -n "$CERTIFICATE" ] || fail "ustaw CERTIFICATE (plik certyfikatu stacji)"
  [ -f "$STATION_KEY" ] || fail "brak pliku klucza: $STATION_KEY"
  [ -f "$CERTIFICATE" ] || fail "brak pliku certyfikatu: $CERTIFICATE"
}

preflight
echo "preflight: OK"

if [ "$DRY_RUN" = "1" ]; then
  simulator --scenario "$SCENARIO" --dry-run \
    --station-key "$STATION_KEY" --certificate "$CERTIFICATE" \
    --verifying-contract "$VERIFYING_CONTRACT"
  echo "dry-run: OK (preflight + plan bez sieci)"
  exit 0
fi

cat <<'STEPS'
Stos stawiamy ręcznie (skrypt go nie startuje):
  1) cd infrastructure/local && cp .env.example .env && docker compose up -d
  2) wdrożenie kontraktu:  cd contracts && forge script script/DeploySupplyChain.s.sol ...
  3) rejestracja stacji:   forge script script/RegisterStation.s.sol ...
  4) backend:              cd backend && ./mvnw spring-boot:run   (wymaga JDK 21 i TEST_DB/DB)
  5) health:               curl -s http://127.0.0.1:8080/actuator/health
STEPS

echo "== health backendu =="
curl -fsS "$BACKEND_URL/actuator/health" >/dev/null || fail "backend nie odpowiada na $BACKEND_URL/actuator/health"
echo "== chainId Anvila =="
cast chain-id --rpc-url "$RPC_URL" | grep -qx "$CHAIN_ID" || fail "chainId RPC != $CHAIN_ID"

echo "== scenariusz =="
simulator --scenario "$SCENARIO" \
  --backend-url "$BACKEND_URL" --rpc-url "$RPC_URL" --chain-id "$CHAIN_ID" \
  --verifying-contract "$VERIFYING_CONTRACT" \
  --station-key "$STATION_KEY" --certificate "$CERTIFICATE"

echo "== podgląd stanu partii (ręcznie) =="
echo "cast call --rpc-url $RPC_URL $VERIFYING_CONTRACT 'getBatch(bytes32)(bool,uint32,bool)' <batchId>"
