#!/usr/bin/env bash
# Exports the ABI and public deployment data of the latest DeploySupplyChain broadcast.
# Usage (from contracts/): script/export-deployment.sh <chainId> <outputName>
#   e.g. script/export-deployment.sh 31337 local.example
#        script/export-deployment.sh 80002 amoy
# Writes ../shared/abi/SupplyChain.abi.json and ../shared/deployments/<outputName>.json.
# Contains only public data: no keys and no RPC URLs.
set -euo pipefail

chain_id="${1:?chainId required}"
name="${2:?output name required}"
cd "$(dirname "$0")/.."

run="broadcast/DeploySupplyChain.s.sol/${chain_id}/run-latest.json"
[[ -f "$run" ]] || { echo "missing $run - run DeploySupplyChain first" >&2; exit 1; }

forge build --quiet
forge inspect SupplyChain abi --json > ../shared/abi/SupplyChain.abi.json

create_tx=$(jq -c '[.transactions[] | select(.transactionType == "CREATE" and .contractName == "SupplyChain")][0]' "$run")
address=$(cast to-check-sum-address "$(jq -r '.contractAddress' <<<"$create_tx")")
tx_hash=$(jq -r '.hash' <<<"$create_tx")
block_hex=$(jq -r --arg h "$tx_hash" '.receipts[] | select(.transactionHash == $h) | .blockNumber' "$run")
block=$((block_hex))
solc=$(sed -n 's/^solc_version *= *"\(.*\)"/\1/p' foundry.toml)
evm=$(sed -n 's/^evm_version *= *"\(.*\)"/\1/p' foundry.toml)
abi_sha=$(shasum -a 256 ../shared/abi/SupplyChain.abi.json | cut -d' ' -f1)

jq -n \
  --argjson chainId "$chain_id" \
  --arg address "$address" \
  --argjson block "$block" \
  --arg tx "$tx_hash" \
  --arg solc "$solc" \
  --arg evm "$evm" \
  --arg forge "$(forge --version | head -1 | sed 's/^forge Version: //')" \
  --arg abiSha "$abi_sha" \
  '{
    contract: "SupplyChain",
    chainId: $chainId,
    address: $address,
    deploymentBlock: $block,
    deploymentTxHash: $tx,
    eip712Domain: {name: "SupplyChain", version: "1", chainId: $chainId, verifyingContract: $address},
    compiler: {solc: $solc, evmVersion: $evm, viaIR: true, optimizerRuns: 200, forge: $forge},
    abi: {file: "shared/abi/SupplyChain.abi.json", sha256: $abiSha}
  }' > "../shared/deployments/${name}.json"

echo "ABI     -> shared/abi/SupplyChain.abi.json"
echo "Deploy  -> shared/deployments/${name}.json (${address} @ block ${block})"
