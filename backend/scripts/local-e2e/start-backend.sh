#!/usr/bin/env bash
# Uruchamia backend w tle i czeka na healthcheck.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

require_tools docker java curl jq

address=$(contract_address)
deployment_block=$(jq -er '.deploymentBlock' "$DEPLOYMENTS_FILE")

info "Buduję i uruchamiam backend (log: $BACKEND_LOG)"
cd "$BACKEND_DIR"
./mvnw -q package -DskipTests
DB_URL="jdbc:postgresql://127.0.0.1:$POSTGRES_PORT/$POSTGRES_DB" \
DB_USER="$POSTGRES_USER" \
DB_PASSWORD="$POSTGRES_PASSWORD" \
CHAIN_RPC_URL="$RPC_URL" \
CONTRACT_ADDRESS="$address" \
CHAIN_DEPLOYMENT_BLOCK="$deployment_block" \
CHAIN_INDEXER_ENABLED=true \
STATION_TRUSTED_ISSUERS="0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266" \
java -jar "$BACKEND_DIR/target/supply-blockchain-backend-0.1.0-SNAPSHOT.jar" > "$BACKEND_LOG" 2>&1 &
backend_pid=$!
echo "$backend_pid" > "$BACKEND_PID_FILE"

info "Czekam na backend $BACKEND_URL (timeout 120s)"
for ((i = 0; i < 120; i++)); do
  if curl -fsS "$BACKEND_URL/actuator/health" >/dev/null 2>&1; then
    info "backend: UP"
    break
  fi
  if ! kill -0 "$backend_pid" 2>/dev/null; then
    die "backend proces $backend_pid zakończył się przed healthcheckiem; log: $BACKEND_LOG"
  fi
  sleep 1
done

curl -fsS "$BACKEND_URL/actuator/health" >/dev/null 2>&1 || die "backend nie osiągnął stanu UP w 120s; log: $BACKEND_LOG"

info "Sprawdzam migracje bazy"
if ! migrations_ready=$(postgres_psql -Atc \
  "SELECT to_regclass('public.station_certificates') IS NOT NULL"); then
  die "nie udało się sprawdzić migracji bazy $POSTGRES_DB; log backendu: $BACKEND_LOG"
fi
[[ "$migrations_ready" == "t" ]] ||
  die "brak tabeli station_certificates w bazie $POSTGRES_DB; log backendu: $BACKEND_LOG"
info "migracje: gotowe"
