#!/usr/bin/env bash
# Uruchamia scenariusz E2E symulatora.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

address=$(contract_address)

info "Uruchamiam scenariusz E2E"
cd "$SIMULATOR_DIR"
.venv/bin/python -m supply_chain_simulator \
  --scenario scenarios/ebike-happy-path.yaml \
  --backend-url "$BACKEND_URL" \
  --rpc-url "$RPC_URL" \
  --chain-id "$CHAIN_ID" \
  --verifying-contract "$address" \
  --station-key "$STATION_KEY" \
  --certificate "$STATION_CERT"
