#!/usr/bin/env bash
# Stawia/zapewnia lokalny stos: Postgres, Anvil, Kubo.
# Idempotentny: można uruchamiać wielokrotnie.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

require_tools docker jq curl

info "Workdir: $E2E_WORKDIR"

[[ -n "$POSTGRES_PASSWORD" ]] || die "POSTGRES_PASSWORD jest puste. Ustaw hasło w $COMPOSE_DIR/.env"

if [[ ! -f "$COMPOSE_DIR/.env" ]]; then
  info "Tworzę $COMPOSE_DIR/.env z domyślnymi wartościami"
  cat > "$COMPOSE_DIR/.env" <<EOF
POSTGRES_DB=supplychain
POSTGRES_USER=$POSTGRES_USER
POSTGRES_PASSWORD=$POSTGRES_PASSWORD
POSTGRES_PORT=$POSTGRES_PORT
ANVIL_CHAIN_ID=$CHAIN_ID
ANVIL_PORT=8545
KUBO_API_PORT=5001
KUBO_GATEWAY_PORT=8081
EOF
fi

info "Uruchamiam Compose (Postgres, Anvil, Kubo)"
docker compose -f "$COMPOSE_DIR/compose.yaml" up -d postgres anvil kubo

info "Reset łańcucha Anvil — świeży stan przy każdym uruchomieniu"
docker compose -f "$COMPOSE_DIR/compose.yaml" restart anvil >/dev/null

wait_for_healthy postgres
wait_for_healthy anvil
wait_for_healthy kubo

block_hex=$(curl -fsS -X POST -H 'content-type: application/json' \
  --data '{"jsonrpc":"2.0","id":1,"method":"eth_blockNumber","params":[]}' \
  "$RPC_URL" | jq -r '.result')
[[ "$block_hex" == "0x0" ]] || die "Anvil nie jest pusty po resecie (blockNumber=$block_hex)"
info "Anvil: pusty łańcuch (block 0)"

info "Ustawiam hasło użytkownika $POSTGRES_USER"
if docker compose -f "$COMPOSE_DIR/compose.yaml" exec -T postgres \
  psql -U "$POSTGRES_USER" -d postgres -c "ALTER USER $POSTGRES_USER WITH PASSWORD '$POSTGRES_PASSWORD';" >/dev/null 2>&1; then
  info "Hasło użytkownika $POSTGRES_USER zaktualizowane"
elif docker compose -f "$COMPOSE_DIR/compose.yaml" exec -T postgres \
  psql -U postgres -d postgres -c "CREATE USER $POSTGRES_USER WITH PASSWORD '$POSTGRES_PASSWORD' SUPERUSER;" >/dev/null 2>&1; then
  info "Utworzono użytkownika $POSTGRES_USER"
else
  die "nie udało się połączyć z Postgres jako $POSTGRES_USER ani postgres. " \
      "Prawdopodobnie wolumen został zainicjalizowany z innym POSTGRES_USER. " \
      "Zresetuj wolumen: cd $COMPOSE_DIR && docker compose down -v"
fi

info "Przygotowuję bazy danych"
for db in "$POSTGRES_DB" "${POSTGRES_DB}_test"; do
  docker compose -f "$COMPOSE_DIR/compose.yaml" exec -T postgres \
    psql -U "$POSTGRES_USER" -d postgres -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='$db' AND pid <> pg_backend_pid();" >/dev/null 2>&1 || true
  docker compose -f "$COMPOSE_DIR/compose.yaml" exec -T postgres \
    psql -U "$POSTGRES_USER" -d postgres -c "DROP DATABASE IF EXISTS $db;" >/dev/null 2>&1 || true
  docker compose -f "$COMPOSE_DIR/compose.yaml" exec -T postgres \
    psql -U "$POSTGRES_USER" -d postgres -c "CREATE DATABASE $db;" >/dev/null
  info "Baza $db: gotowa"
done

info "Stos gotowy (Postgres port $POSTGRES_PORT)"
