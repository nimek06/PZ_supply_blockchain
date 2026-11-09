#!/usr/bin/env bash
# Integration test on an isolated Anvil, using only its public development keys.
set -euo pipefail

contracts="$(cd "$(dirname "$0")/.." && pwd)"
root="$(cd "$contracts/.." && pwd)"
if [[ -d $HOME/.foundry/bin ]]; then PATH="$HOME/.foundry/bin:$PATH"; fi
for tool in anvil forge cast jq shasum; do
  command -v "$tool" >/dev/null || { echo "Missing $tool" >&2; exit 1; }
done

port=${TEST_ANVIL_PORT:-18547}
[[ $port =~ ^[0-9]+$ ]] || { echo "Invalid TEST_ANVIL_PORT" >&2; exit 1; }
rpc="http://127.0.0.1:$port"
if cast chain-id --rpc-url "$rpc" >/dev/null 2>&1; then
  echo "RPC already listening on $rpc; choose another TEST_ANVIL_PORT" >&2
  exit 1
fi

tmp=$(mktemp -d "${TMPDIR:-/tmp}/supply-amoy-smoke.XXXXXX")
anvil_pid=""
cleanup() {
  if [[ -n $anvil_pid ]]; then
    kill "$anvil_pid" 2>/dev/null || true
    wait "$anvil_pid" 2>/dev/null || true
  fi
  rm -rf "$tmp"
}
trap cleanup EXIT

project="$tmp/project"
mkdir -p "$project/contracts" "$project/shared/abi" "$project/shared/deployments" "$project/shared/fixtures"
cp -R "$contracts/src" "$contracts/script" "$contracts/lib" "$project/contracts/"
cp "$contracts/foundry.toml" "$project/contracts/"
cp "$root/shared/abi/SupplyChain.abi.json" "$project/shared/abi/"
printf 'IPFS_CID=\n' >"$project/contracts/.env"
printf '# Inputs are passed in the environment.\n' >"$tmp/empty.env"

anvil --port "$port" --chain-id 80002 --base-fee 64 --block-time 1 --slots-in-an-epoch 1 \
  --silent >"$tmp/anvil.log" 2>&1 &
anvil_pid=$!
sleep 1
for _ in 1 2 3 4 5 6 7 8 9 10; do
  if ! kill -0 "$anvil_pid" 2>/dev/null; then
    cat "$tmp/anvil.log" >&2
    exit 1
  fi
  if [[ $(cast chain-id --rpc-url "$rpc" 2>/dev/null || true) == 80002 ]]; then break; fi
  sleep 1
done
[[ $(cast chain-id --rpc-url "$rpc" 2>/dev/null || true) == 80002 ]] ||
  { echo "Anvil did not become ready" >&2; exit 1; }

admin=0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266
admin_key=0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80
station_key=0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d
no_funds_key=0x0000000000000000000000000000000000000000000000000000000000000001
log="$tmp/smoke.log"

fail() { echo "FAIL: $*" >&2; tail -30 "$log" >&2; exit 1; }
nonce() { cast nonce "$admin" --rpc-url "$rpc"; }
run_script() {
  local mode=$1
  shift
  local args=(env -i HOME="$HOME" PATH="$PATH" ENV_FILE="$tmp/empty.env" AMOY_RPC_URL="$rpc"
    DEPLOYER_PRIVATE_KEY="$admin_key" STATION_PRIVATE_KEY="$station_key"
    BATCH_NUMBER=B-2030-0001 TIP_GWEI=30 "$@" "$project/contracts/script/amoy-smoke.sh")
  case "$mode" in
    normal) ;;
    dry) args+=(--dry-run) ;;
    fresh-dry) args+=(--fresh --dry-run) ;;
    *) fail "invalid test mode $mode" ;;
  esac
  "${args[@]}" >"$log" 2>&1
}
expect_error() {
  local mode=$1 message=$2 before
  shift 2
  before=$(nonce)
  if run_script "$mode" "$@"; then fail "expected error: $message"; fi
  grep -F "$message" "$log" >/dev/null || fail "missing error: $message"
  [[ $(nonce) == "$before" ]] || fail "error sent a transaction"
}

run_script dry || fail "initial dry run"
grep -F "Symulacja DeploySupplyChain.s.sol" "$log" >/dev/null || fail "dry run did not simulate deploy"
[[ $(nonce) == 0 ]] || fail "dry run sent a transaction"

run_script normal || fail "first smoke"
[[ $(nonce) == 5 ]] || fail "expected five transactions"
address=$(jq -er .address "$project/shared/deployments/amoy.json")
batch=$(cast keccak B-2030-0001)
state=$(cast call "$address" 'getBatch(bytes32)(bool,uint32,bool)' "$batch" --rpc-url "$rpc" | tr '\n' ' ')
[[ $state == 'true 1 false ' ]] || fail "unexpected batch state: $state"
smoke_run="$project/contracts/broadcast/SmokeStage.s.sol/80002/run-latest.json"
cid=$(jq -r '.transactions[] | select((.function // "") | startswith("completeStage(")) | .arguments[5]' "$smoke_run")
[[ $cid == bafkreigh2akiscaildcqabsyg3dfr6chu3fgpregiymsck7e7aqa4s52zy ]] ||
  fail "empty .env CID was not replaced with the default"

run_script normal || fail "repeat smoke"
[[ $(nonce) == 5 ]] || fail "repeat smoke was not idempotent"
grep -F "3/4 SmokeStage pominięty" "$log" >/dev/null || fail "repeat smoke did not skip stage"

run_script normal BATCH_NUMBER=B-2030-0002 TIP_GWEI=31.5 || fail "second batch"
[[ $(nonce) == 7 ]] || fail "second batch should send two transactions"
tx=$(jq -er '.transactions[0].hash' "$smoke_run")
fees=$(cast rpc eth_getTransactionByHash "$tx" --rpc-url "$rpc")
tip=$(cast to-dec "$(jq -er .maxPriorityFeePerGas <<<"$fees")")
max_fee=$(cast to-dec "$(jq -er .maxFeePerGas <<<"$fees")")
[[ $tip == 31500000000 ]] && ((max_fee >= tip)) || fail "incorrect EIP-1559 fees"

expect_error fresh-dry "musi zezwalać na etap 0" STATION_STAGE_MASK=2
expect_error fresh-dry "STATION_ID nie może być zerowe" \
  STATION_ID=0x0000000000000000000000000000000000000000000000000000000000000000
expect_error fresh-dry "DEADLINE_SECONDS przekracza" DEADLINE_SECONDS=3601
expect_error fresh-dry "za mało POL" DEPLOYER_PRIVATE_KEY="$no_funds_key"

deploy_run="$project/contracts/broadcast/DeploySupplyChain.s.sol/80002/run-latest.json"
cp "$deploy_run" "$tmp/deploy-backup.json"
jq '(.transactions[] | select(.transactionType == "CREATE") | .hash) =
  "0x1111111111111111111111111111111111111111111111111111111111111111"' \
  "$tmp/deploy-backup.json" >"$deploy_run"
expect_error dry "nie można potwierdzić ostatniego wdrożenia"
cp "$tmp/deploy-backup.json" "$deploy_run"

cast send "$address" 'createBatch(string)' B-2030-0003 --private-key "$admin_key" --rpc-url "$rpc" >/dev/null
expect_error dry "istnieje bez etapu 0" BATCH_NUMBER=B-2030-0003
run_script fresh-dry || fail "fresh dry run"
grep -F "Symulacja DeploySupplyChain.s.sol" "$log" >/dev/null || fail "fresh dry run did not simulate deploy"

echo "PASS: dry run, full smoke, resume, fees and safety checks on isolated Anvil"
