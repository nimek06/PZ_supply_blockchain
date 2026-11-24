#!/usr/bin/env bash
# Pion lokalny jedną komendą: operator tworzy partię, a stacja zgłasza etap 0 i czeka
# na potwierdzenie (nextStage=1). Stos (Compose), kontrakty i backend stawiamy osobno
# (patrz backend/scripts/running-local-e2e.md); ten skrypt tylko wykonuje pion.
#
# Wymaga backendu z WŁĄCZONYM workerem/relayerem i indexerem:
#   RELAYER_WORKER_ENABLED=true RELAYER_PRIVATE_KEY=<klucz Anvil #3> CHAIN_INDEXER_ENABLED=true
# Bez workera scenariusz nie doczeka potwierdzenia - użyj wtedy `run-local-e2e.sh`
# (accept-only) albo scenariusza `ebike-happy-path.yaml`.
#
# Użycie:
#   STATION_KEY=... CERTIFICATE=... VERIFYING_CONTRACT=0x... ./run-vertical.sh
#   ... ./run-vertical.sh --dry-run      # tylko plan pionu, bez sieci
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SIMULATOR_DIR="$REPO_ROOT/simulator"

BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:8080}"
RPC_URL="${RPC_URL:-http://127.0.0.1:8545}"
CHAIN_ID="${CHAIN_ID:-31337}"
VERIFYING_CONTRACT="${VERIFYING_CONTRACT:-}"
STATION_KEY="${STATION_KEY:-}"
CERTIFICATE="${CERTIFICATE:-}"
SCENARIO="${SCENARIO:-scenarios/ebike-vertical.yaml}"
WAIT_ATTEMPTS="${WAIT_ATTEMPTS:-60}"
WAIT_INTERVAL="${WAIT_INTERVAL:-1}"

DRY_RUN=0
if [ "${1:-}" = "--dry-run" ]; then
  DRY_RUN=1
fi

fail() { echo "error: $*" >&2; exit 1; }

simulator() {
  ( cd "$SIMULATOR_DIR" && .venv/bin/python -m supply_chain_simulator "$@" )
}

[ -n "$VERIFYING_CONTRACT" ] || fail "ustaw VERIFYING_CONTRACT (adres z shared/deployments/)"
[ -n "$STATION_KEY" ] || fail "ustaw STATION_KEY (plik klucza stacji 0600)"
[ -n "$CERTIFICATE" ] || fail "ustaw CERTIFICATE (plik certyfikatu stacji)"
[ -f "$STATION_KEY" ] || fail "brak pliku klucza: $STATION_KEY"
[ -f "$CERTIFICATE" ] || fail "brak pliku certyfikatu: $CERTIFICATE"
[ -x "$SIMULATOR_DIR/.venv/bin/python" ] || fail "brak venv symulatora ($SIMULATOR_DIR/.venv)"

# Symulator uruchamiamy z katalogu modułu, więc ścieżki muszą być absolutne.
STATION_KEY="$(realpath "$STATION_KEY")"
CERTIFICATE="$(realpath "$CERTIFICATE")"

if [ "$DRY_RUN" = "1" ]; then
  simulator --scenario "$SCENARIO" --dry-run \
    --station-key "$STATION_KEY" --certificate "$CERTIFICATE" \
    --verifying-contract "$VERIFYING_CONTRACT"
  echo "dry-run: OK (plan pionu bez sieci)"
  exit 0
fi

echo "== health backendu =="
curl -fsS "$BACKEND_URL/actuator/health" >/dev/null || fail "backend nie odpowiada na $BACKEND_URL/actuator/health"
echo "== chainId Anvila =="
cast chain-id --rpc-url "$RPC_URL" | grep -qx "$CHAIN_ID" || fail "chainId RPC != $CHAIN_ID"

echo "== partia (operator) =="
batch_number=$(BACKEND_URL="$BACKEND_URL" "$SIMULATOR_DIR/scripts/create-batch.sh")
echo "partia: $batch_number" >&2

echo "== pion: etap 0 (stacja) =="
simulator --scenario "$SCENARIO" --batch-number "$batch_number" \
  --backend-url "$BACKEND_URL" --rpc-url "$RPC_URL" --chain-id "$CHAIN_ID" \
  --verifying-contract "$VERIFYING_CONTRACT" \
  --station-key "$STATION_KEY" --certificate "$CERTIFICATE" \
  --wait-attempts "$WAIT_ATTEMPTS" --wait-interval "$WAIT_INTERVAL"

echo "== stan partii (ręcznie) =="
echo "curl -sS $BACKEND_URL/api/v1/batch-numbers/$batch_number | jq ."
