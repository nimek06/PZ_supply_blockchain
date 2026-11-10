#!/usr/bin/env bash
# Przygotowuje klucz stacji, wystawia certyfikat i rejestruje stację on-chain.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

require_tools python3 jq

address=$(contract_address)

info "Przygotowuję venv symulatora"
cd "$SIMULATOR_DIR"
if [[ ! -x .venv/bin/python ]]; then
  python3 -m venv .venv
  .venv/bin/pip install -q -e .
fi

info "Tworzę klucz stacji w $STATION_KEY"
mkdir -p "$(dirname "$STATION_KEY")"
cat > "$STATION_KEY" <<EOF
{"address":"$STATION_ADDRESS","privateKey":"$ANVIL_STATION_KEY"}
EOF
chmod 600 "$STATION_KEY"

info "Wystawiam certyfikat stacji"
ADMIN_PRIVATE_KEY="$ANVIL_ADMIN_KEY" .venv/bin/python -m supply_chain_simulator \
  --issue-certificate \
  --station-address "$STATION_ADDRESS" \
  --station-id "$STATION_REFERENCE" \
  --certificate-id "local-e2e-$(date +%s)" \
  --verifying-contract "$address" \
  --issuer-key-env ADMIN_PRIVATE_KEY \
  --allowed-stage-ids 0,1,2,3 \
  --valid-days 365 \
  --certificate-out "$STATION_CERT"

station_id=$(jq -er '.payload.stationId' "$STATION_CERT")
expires_at=$(jq -er '.payload.expiresAt' "$STATION_CERT")
allowed_stage_ids=$(jq -er '.payload.allowedStageIds | join(",")' "$STATION_CERT")

# Wylicz stageMask z allowedStageIds (0,1,2,3 -> 15)
stage_mask=$(python3 -c "ids=[int(x) for x in '$allowed_stage_ids'.split(',')]; print(sum(1 << s for s in ids))")

info "Rejestruję stację on-chain (stationId $station_id, expiresAt $expires_at, mask $stage_mask)"
cd "$CONTRACTS_DIR"
if command -v cast >/dev/null 2>&1; then
  cast send "$address" "registerStation(address,bytes32,uint64,uint32)" \
    "$STATION_ADDRESS" "$station_id" "$expires_at" "$stage_mask" \
    --private-key "$ANVIL_ADMIN_KEY" --rpc-url "$RPC_URL" >/dev/null
else
  network="${COMPOSE_NETWORK:-pz-supply_default}"
  docker run --rm --network "$network" --entrypoint cast ghcr.io/foundry-rs/foundry:v1.8.6 \
    send "$address" "registerStation(address,bytes32,uint64,uint32)" \
    "$STATION_ADDRESS" "$station_id" "$expires_at" "$stage_mask" \
    --private-key "$ANVIL_ADMIN_KEY" --rpc-url http://anvil:8545 >/dev/null
fi

info "Zapisuję certyfikat w bazie $POSTGRES_DB"
python3 - "$STATION_CERT" "$STATION_ADDRESS" "$E2E_WORKDIR/insert_cert.sql" <<'PY'
import json, sys
cert_path, station_address, sql_path = sys.argv[1:4]
cert = json.load(open(cert_path))
cert_json = json.dumps(cert)
cert_id = cert['payload']['certificateId']
with open(sql_path, "w", encoding="utf-8") as f:
    f.write(f"""INSERT INTO station_certificates (certificate_id, station_address, certificate_json, created_at)
VALUES ('{cert_id}', '{station_address}', '{cert_json.replace("'", "''")}', NOW())
ON CONFLICT (certificate_id) DO UPDATE SET
  station_address = EXCLUDED.station_address,
  certificate_json = EXCLUDED.certificate_json,
  created_at = EXCLUDED.created_at;
""")
PY
PGPASSWORD="$POSTGRES_PASSWORD" psql -h 127.0.0.1 -p "$POSTGRES_PORT" -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -f "$E2E_WORKDIR/insert_cert.sql"

info "Stacja gotowa: $STATION_ADDRESS"
