#!/usr/bin/env bash
# Wdraża SupplyChain na lokalnego Anvila i rejestruje stację testową. Odpala go usługa deploy z compose.yaml.
# - kontrakty są zamontowane tylko do odczytu, out/cache/broadcast idą do wolumenu
# - jeśli kontrakt już jest pod oczekiwanym adresem, deploy jest pomijany
# - stacja z shared/fixtures/station-certificate.json (registryCall), RegisterStation sam pomija powtórkę
# - OPERATOR_ROLE dla OPERATOR_ADDRESS (tworzenie partii z UI), pomijane, jeśli konto już ją ma
# - klucz to publiczny klucz Anvil #0, tylko dla lokalnego łańcucha
set -euo pipefail

rpc="${RPC_URL:-http://anvil:8545}"
expected_chain="${EXPECTED_CHAIN_ID:-31337}"
expected="${EXPECTED_CONTRACT_ADDRESS:-0x5FbDB2315678afecb367f032d93F642f64180aa3}"
fixture="${STATION_FIXTURE:-/fixtures/station-certificate.json}"
admin="${ADMIN_ADDRESS:-0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266}"
relayer="${RELAYER_ADDRESS:-0x90F79bf6EB2c4f870365E785982E1f101E93b906}"
qa="${QA_INSPECTOR_ADDRESS:-0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC}"

if [[ ! -f lib/forge-std/src/Script.sol || ! -d lib/openzeppelin-contracts/contracts ]]; then
  echo "brak bibliotek w contracts/lib - odpal najpierw: git submodule update --init" >&2
  exit 1
fi

die() { echo "deploy-contract: $*" >&2; exit 1; }
lower() { tr '[:upper:]' '[:lower:]' <<<"$1"; }

[[ $expected_chain == 31337 ]] || die "lokalny deploy z kluczami Anvila wymaga chainId 31337"

actual_chain="$(cast chain-id --rpc-url "$rpc")"
[[ $actual_chain == "$expected_chain" ]] ||
  die "chainId $actual_chain zamiast oczekiwanego $expected_chain"

for address in "$admin" "$relayer" "$qa"; do
  [[ $address =~ ^0x[0-9a-fA-F]{40}$ ]] || die "niepoprawny adres roli: $address"
done
[[ $(lower "$admin") != "$(lower "$relayer")" &&
   $(lower "$admin") != "$(lower "$qa") &&
   $(lower "$relayer") != "$(lower "$qa") ]] ||
  die "admin, relayer i QA muszą używać oddzielnych adresów"

if [[ "$(cast code "$expected" --rpc-url "$rpc")" != "0x" ]]; then
  echo "SupplyChain już jest pod $expected, pomijam deploy"
else
  ADMIN_ADDRESS="$admin" RELAYER_ADDRESS="$relayer" QA_INSPECTOR_ADDRESS="$qa" \
    forge script script/DeploySupplyChain.s.sol --rpc-url "$rpc" --broadcast
  if [[ "$(cast code "$expected" --rpc-url "$rpc")" == "0x" ]]; then
    echo "kontrakt nie trafił pod $expected (łańcuch nie był pusty?) - zrób reset Anvila, patrz README" >&2
    exit 1
  fi
  echo "SupplyChain wdrożony pod $expected"
fi

has_role() {
  [[ $(cast call "$expected" "hasRole(bytes32,address)(bool)" "$1" "$2" --rpc-url "$rpc") == true ]]
}

ensure_role() {
  local role_name=$1 account=$2 role_hash
  role_hash="$(cast call "$expected" "$role_name()(bytes32)" --rpc-url "$rpc")"
  if ! has_role "$role_hash" "$account"; then
    cast send "$expected" "grantRole(bytes32,address)" "$role_hash" "$account" \
      --private-key "$DEPLOYER_PRIVATE_KEY" --rpc-url "$rpc" >/dev/null
  fi
  has_role "$role_hash" "$account" || die "$account nie ma roli $role_name"
}

revoke_role() {
  local role_name=$1 account=$2 role_hash
  role_hash="$(cast call "$expected" "$role_name()(bytes32)" --rpc-url "$rpc")"
  if has_role "$role_hash" "$account"; then
    cast send "$expected" "revokeRole(bytes32,address)" "$role_hash" "$account" \
      --private-key "$DEPLOYER_PRIVATE_KEY" --rpc-url "$rpc" >/dev/null
  fi
  ! has_role "$role_hash" "$account" || die "$account nadal ma rolę $role_name"
}

has_role 0x0000000000000000000000000000000000000000000000000000000000000000 "$admin" ||
  die "$admin nie ma DEFAULT_ADMIN_ROLE"
ensure_role RELAYER_ROLE "$relayer"
revoke_role RELAYER_ROLE "$admin"
ensure_role QA_INSPECTOR_ROLE "$qa"

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
[[ $STATION_STAGE_MASK == 15 ]] || die "certyfikat testowy musi zezwalać na etapy 0..3 (stageMask=15)"
for address in "$admin" "$relayer" "$qa"; do
  [[ $(lower "$STATION_ADDRESS") != "$(lower "$address")" ]] ||
    die "admin, relayer, stacja i QA muszą używać oddzielnych adresów"
done

ADMIN_PRIVATE_KEY="$DEPLOYER_PRIVATE_KEY" SUPPLY_CHAIN_ADDRESS="$expected" \
  STATION_ADDRESS="$STATION_ADDRESS" STATION_ID="$STATION_ID" \
  STATION_EXPIRES_AT="$STATION_EXPIRES_AT" STATION_STAGE_MASK="$STATION_STAGE_MASK" \
  forge script script/RegisterStation.s.sol --rpc-url "$rpc" --broadcast
echo "stacja $STATION_ADDRESS zarejestrowana (etapy: maska $STATION_STAGE_MASK)"

# rola operatora produkcji: bez niej POST /batches zwraca 403 ROLE_MISSING
if [[ -n "${OPERATOR_ADDRESS:-}" ]]; then
  operator_role="$(cast keccak OPERATOR_ROLE)"
  if [[ "$(cast call "$expected" 'hasRole(bytes32,address)(bool)' "$operator_role" "$OPERATOR_ADDRESS" --rpc-url "$rpc")" == "true" ]]; then
    echo "$OPERATOR_ADDRESS już ma OPERATOR_ROLE"
  else
    cast send "$expected" 'grantRole(bytes32,address)' "$operator_role" "$OPERATOR_ADDRESS" \
      --private-key "$DEPLOYER_PRIVATE_KEY" --rpc-url "$rpc" > /dev/null
    echo "OPERATOR_ROLE nadana dla $OPERATOR_ADDRESS"
  fi
fi
