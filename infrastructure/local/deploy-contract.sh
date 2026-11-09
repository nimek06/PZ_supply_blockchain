#!/usr/bin/env bash
# Wdraża SupplyChain na lokalnego Anvila i rejestruje stację testową. Odpala go usługa deploy z compose.yaml.
# - kontrakty są zamontowane tylko do odczytu, out/cache/broadcast idą do wolumenu
# - jeśli kontrakt już jest pod oczekiwanym adresem, deploy jest pomijany
# - stacja z shared/fixtures/station-certificate.json (registryCall), RegisterStation sam pomija powtórkę
# - klucz to publiczny klucz Anvil #0, tylko dla lokalnego łańcucha
set -euo pipefail

rpc="${RPC_URL:-http://anvil:8545}"
expected="${EXPECTED_CONTRACT_ADDRESS:-0x5FbDB2315678afecb367f032d93F642f64180aa3}"
fixture="${STATION_FIXTURE:-/fixtures/station-certificate.json}"

if [[ ! -f lib/forge-std/src/Script.sol || ! -d lib/openzeppelin-contracts/contracts ]]; then
  echo "brak bibliotek w contracts/lib - odpal najpierw: git submodule update --init" >&2
  exit 1
fi

if [[ "$(cast code "$expected" --rpc-url "$rpc")" != "0x" ]]; then
  echo "SupplyChain już jest pod $expected, pomijam deploy"
else
  forge script script/DeploySupplyChain.s.sol --rpc-url "$rpc" --broadcast
  if [[ "$(cast code "$expected" --rpc-url "$rpc")" == "0x" ]]; then
    echo "kontrakt nie trafił pod $expected (łańcuch nie był pusty?) - zrób reset Anvila, patrz README" >&2
    exit 1
  fi
  echo "SupplyChain wdrożony pod $expected"
fi

# wartość pola z bloku "registryCall" fixture'u (plik generuje symulator, jedno pole na linię)
registry_field() {
  sed -n '/"registryCall"/,/}/p' "$fixture" | sed -n "s/^ *\"$1\": *\"\{0,1\}\([0-9a-fA-Fx]*\)\"\{0,1\},\{0,1\} *$/\1/p"
}

STATION_ADDRESS="$(registry_field station)"
STATION_ID="$(registry_field stationId)"
STATION_EXPIRES_AT="$(registry_field expiresAt)"
STATION_STAGE_MASK="$(registry_field stageMask)"
for value in "$STATION_ADDRESS" "$STATION_ID" "$STATION_EXPIRES_AT" "$STATION_STAGE_MASK"; do
  if [[ -z "$value" ]]; then
    echo "nie udało się odczytać registryCall z $fixture" >&2
    exit 1
  fi
done

ADMIN_PRIVATE_KEY="$DEPLOYER_PRIVATE_KEY" SUPPLY_CHAIN_ADDRESS="$expected" \
  STATION_ADDRESS="$STATION_ADDRESS" STATION_ID="$STATION_ID" \
  STATION_EXPIRES_AT="$STATION_EXPIRES_AT" STATION_STAGE_MASK="$STATION_STAGE_MASK" \
  forge script script/RegisterStation.s.sol --rpc-url "$rpc" --broadcast
echo "stacja $STATION_ADDRESS zarejestrowana (etapy: maska $STATION_STAGE_MASK)"
