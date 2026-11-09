#!/usr/bin/env bash
# Lokalne wdrożenie SupplyChain jednym poleceniem: kontrakt, role, stacja ST-001
# i shared/deployments/local.json. Cel: Anvil z Compose (infrastructure/local) albo `anvil`.
#
# Użycie (z dowolnego katalogu):
#   contracts/script/deploy-local.sh            forge/cast z hosta (gdy są w PATH)
#   contracts/script/deploy-local.sh --docker   forge/cast z obrazu Foundry w sieci Compose
#   contracts/script/deploy-local.sh --fresh    świadomie nowy kontrakt (nowy adres)
# Zmienne (opcjonalne):
#   RPC_URL                domyślnie http://127.0.0.1:${ANVIL_PORT:-8545}, z --docker http://anvil:8545
#   LOCAL_RELAYER_ADDRESS  konto z RELAYER_ROLE (domyślnie Anvil #0, jak w DeploySupplyChain)
#   COMPOSE_NETWORK        sieć Compose dla --docker (domyślnie pz-supply_default)
#
# Ponowne uruchomienie nie tworzy nowego kontraktu: jeśli ostatnie wdrożenie z broadcast/
# istnieje na tym łańcuchu i ma kod z bieżącego src/, skrypt tylko sprawdza role i stację.
# Używa PUBLICZNYCH kluczy deweloperskich Anvila, więc działa wyłącznie na chainId 31337.
set -euo pipefail

usage() { awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; }
die() { echo "deploy-local: $*" >&2; exit 1; }
info() { echo "==> $*"; }
warn() { echo "UWAGA: $*" >&2; }
lower() { tr '[:upper:]' '[:lower:]' <<<"$1"; }

fresh=0
use_docker=auto
for arg in "$@"; do
  case "$arg" in
    --fresh) fresh=1 ;;
    --docker) use_docker=1 ;;
    -h | --help) usage; exit 0 ;;
    *) die "nieznany argument: $arg (zobacz --help)" ;;
  esac
done

contracts_dir="$(cd "$(dirname "$0")/.." && pwd)"
root="$(cd "$contracts_dir/.." && pwd)"
cd "$contracts_dir"

readonly LOCAL_CHAIN_ID=31337
# Publiczne klucze deweloperskie Anvila ("test test ... junk") - tylko dla chainId 31337.
readonly ANVIL_ADMIN_KEY=0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80
readonly ANVIL_ADMIN=0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266 # #0: admin i wystawca
readonly ANVIL_QA=0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC    # #2: operator zmiany (QA)
readonly FIXTURE_ADDRESS=0x5FbDB2315678afecb367f032d93F642f64180aa3 # #0 przy nonce 0
readonly foundry_image=ghcr.io/foundry-rs/foundry:v1.8.6          # jak w compose.yaml
readonly DEADLINE_WINDOW=3600
relayer="${LOCAL_RELAYER_ADDRESS:-$ANVIL_ADMIN}"

command -v jq >/dev/null || die "brak jq (macOS 15+ ma wbudowany; inaczej brew/apt install jq)"
command -v shasum >/dev/null || die "brak shasum (potrzebny w export-deployment.sh)"
[[ -f lib/forge-std/src/Script.sol && -d lib/openzeppelin-contracts/contracts ]] ||
  die "brak zależności w contracts/lib - uruchom: git submodule update --init --recursive"

# Stacja ST-001 dokładnie tak, jak oczekuje certyfikat testowy (registryCall).
fixture="$root/shared/fixtures/station-certificate.json"
station=$(jq -er '.registryCall.station' "$fixture")
station_id=$(jq -er '.registryCall.stationId' "$fixture")
station_expires=$(jq -er '.registryCall.expiresAt' "$fixture")
station_mask=$(jq -er '.registryCall.stageMask' "$fixture")

[[ $relayer =~ ^0x[0-9a-fA-F]{40}$ ]] || die "LOCAL_RELAYER_ADDRESS ma zły format: $relayer"
case "$(lower "$relayer")" in
  "$(lower "$station")" | "$(lower "$ANVIL_QA")")
    die "relayer musi mieć inny klucz niż stacja i operator QA (docs/access-control.md)" ;;
esac

if [[ $use_docker == auto ]]; then
  [[ -d $HOME/.foundry/bin ]] && PATH="$HOME/.foundry/bin:$PATH"
  if command -v forge >/dev/null && command -v cast >/dev/null; then
    use_docker=0
  elif command -v docker >/dev/null; then
    use_docker=1
    info "Brak forge/cast na hoście - używam obrazu $foundry_image"
  else
    die "brak forge/cast (zainstaluj: foundryup) i brak dockera"
  fi
fi

if [[ $use_docker == 1 ]]; then
  command -v docker >/dev/null || die "brak dockera"
  network="${COMPOSE_NETWORK:-pz-supply_default}"
  docker network inspect "$network" >/dev/null 2>&1 ||
    die "brak sieci $network - najpierw: cd infrastructure/local && docker compose up -d"
  RPC_URL="${RPC_URL:-http://anvil:8545}"
  # Repo pod tą samą ścieżką co na hoście; katalog domowy w wolumenie, żeby solc pobrać raz.
  # Zmienne skryptów przekazujemy jawnie, inaczej forge w kontenerze sięgnąłby do contracts/.env.
  foundry_run() {
    local tool=$1
    shift
    docker run --rm --network "$network" -v "$root:$root" -w "$PWD" \
      -v pz-supply-foundry-home:/home/foundry \
      -e DEPLOYER_PRIVATE_KEY -e EXPECTED_CHAIN_ID -e RELAYER_ADDRESS -e QA_INSPECTOR_ADDRESS \
      -e ADMIN_ADDRESS -e MAX_DEADLINE_WINDOW -e ADMIN_PRIVATE_KEY -e SUPPLY_CHAIN_ADDRESS \
      -e STATION_ADDRESS -e STATION_ID -e STATION_EXPIRES_AT -e STATION_STAGE_MASK \
      --entrypoint "$tool" "$foundry_image" "$@"
  }
  forge() { foundry_run forge "$@"; }
  cast() { foundry_run cast "$@"; }
  export network root foundry_image
  export -f foundry_run forge cast # export-deployment.sh też ich użyje
else
  command -v forge >/dev/null && command -v cast >/dev/null ||
    die "brak forge/cast w PATH (foundryup) - albo uruchom z --docker"
  RPC_URL="${RPC_URL:-http://127.0.0.1:${ANVIL_PORT:-8545}}"
fi

info "Czekam na RPC $RPC_URL"
chain_id=""
for _ in $(seq 1 30); do
  chain_id=$(cast chain-id --rpc-url "$RPC_URL" 2>/dev/null) && break
  chain_id=""
  sleep 1
done
[[ -n $chain_id ]] ||
  die "RPC $RPC_URL nie odpowiada - uruchom Anvil (docker compose up -d w infrastructure/local albo anvil)"
[[ $chain_id == "$LOCAL_CHAIN_ID" ]] ||
  die "chainId $chain_id zamiast $LOCAL_CHAIN_ID - skrypt używa publicznych kluczy Anvila, tylko lokalnie"

run_file="broadcast/DeploySupplyChain.s.sol/$LOCAL_CHAIN_ID/run-latest.json"
create_tx='[.transactions[] | select(.transactionType == "CREATE" and .contractName == "SupplyChain")][0]'
address=""
stale_code=0

# Ostatnie wdrożenie z broadcast/ musi istnieć na TYM łańcuchu (receipt, kod, STAGE_COUNT)
# i pochodzić z bieżącego src/ (initcode zaczyna się od aktualnego bytecode).
# Wołana w `if`, więc set -e tu nie działa: każdy krok sprawdzamy jawnie.
find_existing() {
  [[ -f $run_file ]] || return 1
  local tx addr input receipt_addr code bytecode
  tx=$(jq -r "$create_tx.hash // empty" "$run_file")
  addr=$(jq -r "$create_tx.contractAddress // empty" "$run_file")
  input=$(jq -r "$create_tx.transaction.input // empty" "$run_file")
  [[ -n $tx && -n $addr && -n $input ]] || return 1
  receipt_addr=$(cast rpc eth_getTransactionReceipt "$tx" --rpc-url "$RPC_URL" |
    jq -r '.contractAddress // empty') || return 1
  [[ -n $receipt_addr && $(lower "$receipt_addr") == "$(lower "$addr")" ]] || return 1
  code=$(cast code "$addr" --rpc-url "$RPC_URL") || return 1
  [[ -n $code && $code != 0x ]] || return 1
  [[ $(cast call "$addr" "STAGE_COUNT()(uint32)" --rpc-url "$RPC_URL") == 4 ]] || return 1
  forge build --quiet || die "kompilacja kontraktów nie powiodła się"
  bytecode=$(forge inspect SupplyChain bytecode) || die "forge inspect nie zwrócił bytecode"
  [[ $bytecode == 0x?* ]] || die "forge inspect zwrócił pusty bytecode"
  if [[ $(lower "$input") != "$(lower "$bytecode")"* ]]; then
    stale_code=1
    return 1
  fi
  address=$addr
}

if [[ $fresh == 0 ]] && find_existing; then
  info "SupplyChain już wdrożony pod $address - sprawdzam role i stację (nowy kontrakt: --fresh)"
else
  if [[ $fresh == 0 ]]; then
    [[ $stale_code == 0 ]] ||
      die "SupplyChain w src/ różni się od wdrożonego - nowy kontrakt: --fresh albo restart Anvila"
    nonce=$(cast nonce "$ANVIL_ADMIN" --rpc-url "$RPC_URL")
    [[ $nonce == 0 ]] || die "konto Anvil #0 ma już $nonce transakcji, a broadcast/ nie zna wdrożenia \
na tym łańcuchu. Nowy kontrakt świadomie: --fresh (adres inny niż $FIXTURE_ADDRESS); \
czysty łańcuch: docker compose restart anvil"
  fi
  info "Wdrażam SupplyChain (DeploySupplyChain.s.sol)"
  (
    export DEPLOYER_PRIVATE_KEY=$ANVIL_ADMIN_KEY EXPECTED_CHAIN_ID=$LOCAL_CHAIN_ID \
      RELAYER_ADDRESS=$relayer QA_INSPECTOR_ADDRESS=$ANVIL_QA ADMIN_ADDRESS=$ANVIL_ADMIN \
      MAX_DEADLINE_WINDOW=$DEADLINE_WINDOW
    forge script script/DeploySupplyChain.s.sol --rpc-url "$RPC_URL" --broadcast
  )
  address=$(jq -r "$create_tx.contractAddress" "$run_file")
fi

has_role() {
  [[ $(cast call "$address" "hasRole(bytes32,address)(bool)" "$1" "$2" --rpc-url "$RPC_URL") == true ]]
}
grant_if_missing() {
  local role
  role=$(cast call "$address" "$1()(bytes32)" --rpc-url "$RPC_URL")
  has_role "$role" "$2" && return
  info "Nadaję $1 kontu $2"
  cast send "$address" "grantRole(bytes32,address)" "$role" "$2" \
    --private-key "$ANVIL_ADMIN_KEY" --rpc-url "$RPC_URL" >/dev/null
  has_role "$role" "$2" || die "nie udało się nadać $1 kontu $2"
}

has_role 0x0000000000000000000000000000000000000000000000000000000000000000 "$ANVIL_ADMIN" ||
  die "Anvil #0 nie ma DEFAULT_ADMIN_ROLE w $address - nowy kontrakt: --fresh albo restart Anvila"
grant_if_missing RELAYER_ROLE "$relayer"
grant_if_missing QA_INSPECTOR_ROLE "$ANVIL_QA"

info "Stacja ST-001 zgodnie z registryCall z shared/fixtures/station-certificate.json"
(
  export ADMIN_PRIVATE_KEY=$ANVIL_ADMIN_KEY SUPPLY_CHAIN_ADDRESS=$address STATION_ADDRESS=$station \
    STATION_ID=$station_id STATION_EXPIRES_AT=$station_expires STATION_STAGE_MASK=$station_mask
  forge script script/RegisterStation.s.sol --rpc-url "$RPC_URL" --broadcast
)
expected_station=$(printf '%s\n' true "$station_id" "$station_expires" "$station_mask")
actual_station=$(cast call "$address" "getStation(address)(bool,bytes32,uint64,uint32)" "$station" \
  --rpc-url "$RPC_URL" | awk '{ print $1 }')
[[ $(lower "$actual_station") == "$(lower "$expected_station")" ]] ||
  die "rejestr stacji niezgodny z registryCall: $(tr '\n' ' ' <<<"$actual_station")"

info "Eksport ABI i shared/deployments/local.json"
script/export-deployment.sh "$LOCAL_CHAIN_ID" local
if command -v git >/dev/null && ! git -C "$root" diff --quiet -- shared/abi/SupplyChain.abi.json; then
  warn "shared/abi/SupplyChain.abi.json się zmienił - commit razem z kontraktem, powiadom backend"
fi

deployed=$(jq -r '.address' "$root/shared/deployments/local.json")
block=$(jq -r '.deploymentBlock' "$root/shared/deployments/local.json")
echo
echo "Gotowe: SupplyChain $deployed na chainId $LOCAL_CHAIN_ID (blok wdrożenia $block)"
echo "  admin, wystawca  $ANVIL_ADMIN (Anvil #0)"
echo "  relayer          $relayer"
echo "  QA               $ANVIL_QA (Anvil #2)"
echo "  stacja ST-001    $station (Anvil #1), ważna do $station_expires, maska $station_mask"
echo "Backend: CHAIN_ID=$LOCAL_CHAIN_ID CONTRACT_ADDRESS=$(lower "$deployed")"
if [[ $(lower "$deployed") != "$(lower "$FIXTURE_ADDRESS")" ]]; then
  warn "adres różni się od $FIXTURE_ADDRESS z fixture'ów i application-local.yaml -" \
    "ustaw CONTRACT_ADDRESS; podpisy z fixture'ów nie przejdą na tym kontrakcie"
fi
