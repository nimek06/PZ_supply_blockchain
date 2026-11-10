# Wspólne ustawienia dla skryptów local-e2e.
# Źródło (source) z pozostałych skryptów w tym katalogu.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
BACKEND_DIR="$REPO_ROOT/backend"
SIMULATOR_DIR="$REPO_ROOT/simulator"
COMPOSE_DIR="$REPO_ROOT/infrastructure/local"
CONTRACTS_DIR="$REPO_ROOT/contracts"
DEPLOYMENTS_FILE="$REPO_ROOT/shared/deployments/local.json"

# Załaduj .env z Compose, jeśli istnieje — żeby E2E używało tych samych wartości.
if [[ -f "$COMPOSE_DIR/.env" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "$COMPOSE_DIR/.env"
  set +a
fi

E2E_WORKDIR="${E2E_WORKDIR:-$REPO_ROOT/backend/scripts/tmp/e2e-run-$(date +%s)}"
mkdir -p "$E2E_WORKDIR"
chmod 700 "$E2E_WORKDIR"
STATION_KEY="${STATION_KEY:-$E2E_WORKDIR/station.key}"
STATION_CERT="${STATION_CERT:-$E2E_WORKDIR/station-certificate.json}"
BACKEND_LOG="${BACKEND_LOG:-$E2E_WORKDIR/backend.log}"
BACKEND_PID_FILE="$E2E_WORKDIR/backend.pid"

POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_USER="${POSTGRES_USER:-supplychain}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-supplychain}"
POSTGRES_DB="${POSTGRES_DB:-supplychain_e2e}"

BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:8080}"
RPC_URL="${RPC_URL:-http://127.0.0.1:8545}"
CHAIN_ID="${CHAIN_ID:-31337}"

ANVIL_ADMIN_KEY="${ANVIL_ADMIN_KEY:-0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80}"
ANVIL_STATION_KEY="${ANVIL_STATION_KEY:-0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d}"
STATION_ADDRESS="${STATION_ADDRESS:-0x70997970C51812dc3A010C7d01b50e0d17dc79C8}"
STATION_REFERENCE="${STATION_REFERENCE:-ST-001}"

info() { echo "==> $*"; }
warn() { echo "UWAGA: $*" >&2; }
die() { echo "error: $*" >&2; exit 1; }

require_tools() {
  for tool in "$@"; do
    command -v "$tool" >/dev/null 2>&1 || die "brak narzędzia: $tool"
  done
}

wait_for_healthy() {
  local service=$1
  local timeout=${2:-60}
  info "Czekam na $service (timeout ${timeout}s)"
  for ((i = 0; i < timeout; i++)); do
    local status
    status=$(docker compose -f "$COMPOSE_DIR/compose.yaml" ps --format json 2>/dev/null | \
      jq -ser '[.[] | select(.Service == "'"$service"'")] | first | .Health // ""' 2>/dev/null || true)
    if [[ "$status" == "healthy" ]]; then
      info "$service: healthy"
      return 0
    fi
    sleep 1
  done
  die "$service nie osiągnął stanu healthy w ${timeout}s"
}

contract_address() {
  [[ -f "$DEPLOYMENTS_FILE" ]] || die "brak $DEPLOYMENTS_FILE - najpierw deploy kontraktu"
  jq -er '.address' "$DEPLOYMENTS_FILE"
}
