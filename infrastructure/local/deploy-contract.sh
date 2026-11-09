#!/usr/bin/env bash
# Wdraża SupplyChain na lokalnego Anvila. Odpala go usługa deploy z compose.yaml.
# - kontrakty są zamontowane tylko do odczytu, out/cache/broadcast idą do wolumenu
# - jeśli kontrakt już jest pod oczekiwanym adresem, nic nie robi
# - klucz to publiczny klucz Anvil #0, tylko dla lokalnego łańcucha
set -euo pipefail

rpc="${RPC_URL:-http://anvil:8545}"
expected="${EXPECTED_CONTRACT_ADDRESS:-0x5FbDB2315678afecb367f032d93F642f64180aa3}"

if [[ "$(cast code "$expected" --rpc-url "$rpc")" != "0x" ]]; then
  echo "SupplyChain już jest pod $expected, pomijam deploy"
  exit 0
fi

if [[ ! -f lib/forge-std/src/Script.sol || ! -d lib/openzeppelin-contracts/contracts ]]; then
  echo "brak bibliotek w contracts/lib - odpal najpierw: git submodule update --init" >&2
  exit 1
fi

forge script script/DeploySupplyChain.s.sol --rpc-url "$rpc" --broadcast

if [[ "$(cast code "$expected" --rpc-url "$rpc")" == "0x" ]]; then
  echo "kontrakt nie trafił pod $expected (łańcuch nie był pusty?) - zrób reset Anvila, patrz README" >&2
  exit 1
fi
echo "SupplyChain wdrożony pod $expected"
