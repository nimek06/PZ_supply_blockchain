#!/usr/bin/env bash
# Smoke na Polygon Amoy jednym poleceniem: DeploySupplyChain, RegisterStation, SmokeStage
# (partia z etapem 0) i export-deployment.sh 80002 amoy, czyli shared/deployments/amoy.json.
#
# Użycie (z dowolnego katalogu):
#   contracts/script/amoy-smoke.sh --dry-run  kontrole i symulacja pierwszego kroku, bez transakcji
#   contracts/script/amoy-smoke.sh            prawdziwe transakcje (POL z faucetu)
#   contracts/script/amoy-smoke.sh --fresh    świadomie nowy kontrakt mimo wdrożenia w broadcast/
# Zmienne z contracts/.env (wzór: .env.example; inny plik: ENV_FILE=ścieżka):
#   DEPLOYER_PRIVATE_KEY  konto z POL; puste ADMIN_PRIVATE_KEY i RELAYER_PRIVATE_KEY = ten klucz
#   STATION_PRIVATE_KEY   testowy klucz stacji bez środków (nowy: cast wallet new)
#   AMOY_RPC_URL          domyślnie publicnode; puste STATION_ID = keccak256("ST-001")
#   TIP_GWEI              priority fee; domyślnie max(30 gwei, eth_maxPriorityFeePerGas)
#
# Ponowne uruchomienie nie tworzy drugiego kontraktu: jeśli ostatnie wdrożenie z broadcast/
# istnieje na Amoy i ma kod z bieżącego src/, skrypt pomija gotowe kroki. Kluczy nie wypisuje.
set -euo pipefail

usage() { awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "$0"; }
die() { echo "amoy-smoke: $*" >&2; exit 1; }
info() { echo "==> $*"; }
warn() { echo "UWAGA: $*" >&2; }
lower() { tr '[:upper:]' '[:lower:]' <<<"$1"; }
same() { [[ $(lower "$1") == "$(lower "$2")" ]]; }
gwei() { awk -v w="$1" 'BEGIN { printf "%.2f", w / 1e9 }'; }
decimal_le() {
  if ((${#1} != ${#2})); then ((${#1} < ${#2})); else [[ $1 == "$2" || $1 < $2 ]]; fi
}
positive_uint() {
  local encoded value
  [[ $2 =~ ^[0-9]+$ ]] || die "$1 musi być dodatnią liczbą całkowitą"
  encoded=$(cast to-uint256 "$2" 2>/dev/null) || die "$1 nie mieści się w uint256"
  value=$(cast to-dec "$encoded")
  [[ $value != 0 ]] || die "$1 musi być większe od zera"
  printf '%s\n' "$value"
}

dry_run=0
fresh=0
for arg in "$@"; do
  case "$arg" in
    --dry-run) dry_run=1 ;;
    --fresh) fresh=1 ;;
    -h | --help) usage; exit 0 ;;
    *) die "nieznany argument: $arg (zobacz --help)" ;;
  esac
done

contracts_dir="$(cd "$(dirname "$0")/.." && pwd)"
root="$(cd "$contracts_dir/.." && pwd)"
cd "$contracts_dir"

readonly CHAIN_ID=80002
readonly MIN_TIP_WEI=30000000000 # DEC-11: 30 gwei zmierzone na Amoy
readonly EXPLORER=https://amoy.polygonscan.com
readonly ZERO_ADDRESS=0x0000000000000000000000000000000000000000
readonly ADMIN_ROLE=0x0000000000000000000000000000000000000000000000000000000000000000
# Gaz z docs/weekly_plans/week2/adam-gas-report.md. Forge ustawia limit = szacunek × 1,3,
# a węzeł sprawdza saldo względem limitu, więc wymagane saldo liczymy z tym zapasem.
readonly GAS_DEPLOY=1453011 GAS_GRANT=51134 GAS_REGISTER=71894 GAS_SMOKE=99584

if [[ -d $HOME/.foundry/bin ]]; then PATH="$HOME/.foundry/bin:$PATH"; fi
command -v forge >/dev/null && command -v cast >/dev/null || die "brak forge/cast w PATH (foundryup)"
command -v jq >/dev/null || die "brak jq (macOS 15+ ma wbudowany; inaczej brew/apt install jq)"
command -v shasum >/dev/null || die "brak shasum (potrzebny w export-deployment.sh)"
[[ -f lib/forge-std/src/Script.sol && -d lib/openzeppelin-contracts/contracts ]] ||
  die "brak zależności w contracts/lib - uruchom: git submodule update --init --recursive"

env_file="${ENV_FILE:-$contracts_dir/.env}"
if [[ -f $env_file ]]; then
  set -a
  # shellcheck source=/dev/null
  . "$env_file"
  set +a
elif [[ -n ${ENV_FILE:-} ]]; then
  die "brak pliku $env_file"
fi

[[ -n ${DEPLOYER_PRIVATE_KEY:-} ]] ||
  die "brak DEPLOYER_PRIVATE_KEY (konto z POL) - uzupełnij $env_file według .env.example"
[[ -n ${STATION_PRIVATE_KEY:-} ]] ||
  die "brak STATION_PRIVATE_KEY - testowy klucz stacji bez środków (nowy: cast wallet new)"
ADMIN_PRIVATE_KEY=${ADMIN_PRIVATE_KEY:-$DEPLOYER_PRIVATE_KEY}
RELAYER_PRIVATE_KEY=${RELAYER_PRIVATE_KEY:-$DEPLOYER_PRIVATE_KEY}

addr_of() {
  [[ $2 =~ ^0x[0-9a-fA-F]{64}$ ]] || die "$1 musi mieć postać 0x i 64 znaki hex"
  cast wallet address --private-key "$2"
}
deployer=$(addr_of DEPLOYER_PRIVATE_KEY "$DEPLOYER_PRIVATE_KEY")
admin=$(addr_of ADMIN_PRIVATE_KEY "$ADMIN_PRIVATE_KEY")
relayer=$(addr_of RELAYER_PRIVATE_KEY "$RELAYER_PRIVATE_KEY")
station=$(addr_of STATION_PRIVATE_KEY "$STATION_PRIVATE_KEY")

admin_cfg=${ADMIN_ADDRESS:-$deployer}
relayer_cfg=${RELAYER_ADDRESS:-$deployer}
qa=${QA_INSPECTOR_ADDRESS:-$ZERO_ADDRESS}
for a in "$admin_cfg" "$relayer_cfg" "$qa" "${STATION_ADDRESS:-$station}"; do
  [[ $a =~ ^0x[0-9a-fA-F]{40}$ ]] || die "zły format adresu: $a"
done
same "$admin" "$admin_cfg" ||
  die "ADMIN_PRIVATE_KEY nie należy do admina $admin_cfg (ADMIN_ADDRESS, domyślnie deployer)"
same "$relayer" "$relayer_cfg" ||
  die "RELAYER_PRIVATE_KEY nie należy do relayera $relayer_cfg (RELAYER_ADDRESS, domyślnie deployer)"
same "$station" "${STATION_ADDRESS:-$station}" ||
  die "STATION_ADDRESS różni się od adresu klucza STATION_PRIVATE_KEY ($station)"
for a in "$deployer" "$admin" "$relayer" "$qa"; do
  if same "$a" "$station"; then
    die "stacja $station musi mieć inny klucz niż deployer, admin, relayer i QA (docs/access-control.md)"
  fi
done
if same "$qa" "$relayer"; then die "relayer i QA muszą mieć różne klucze (docs/access-control.md)"; fi

STATION_ID=${STATION_ID:-$(cast keccak ST-001)}
[[ $STATION_ID =~ ^0x[0-9a-fA-F]{64}$ ]] || die "STATION_ID musi być bytes32 (0x i 64 znaki hex)"
same "$STATION_ID" "$ADMIN_ROLE" && die "STATION_ID nie może być zerowe"
STATION_STAGE_MASK=${STATION_STAGE_MASK:-15}
[[ $STATION_STAGE_MASK =~ ^([1-9]|1[0-5])$ ]] || die "STATION_STAGE_MASK musi być liczbą 1-15"
((STATION_STAGE_MASK & 1)) || die "STATION_STAGE_MASK musi zezwalać na etap 0 smoke"
requested_expiry=${STATION_EXPIRES_AT:-}
if [[ -n $requested_expiry ]]; then
  requested_expiry=$(positive_uint STATION_EXPIRES_AT "$requested_expiry")
  expiry_hex=$(cast to-uint256 "$requested_expiry")
  [[ ${expiry_hex:2:48} == "${ADMIN_ROLE:2:48}" ]] || die "STATION_EXPIRES_AT nie mieści się w uint64"
fi
MAX_DEADLINE_WINDOW=$(positive_uint MAX_DEADLINE_WINDOW "${MAX_DEADLINE_WINDOW:-3600}")
DEADLINE_SECONDS=$(positive_uint DEADLINE_SECONDS "${DEADLINE_SECONDS:-900}")
IPFS_CID=${IPFS_CID:-bafkreigh2akiscaildcqabsyg3dfr6chu3fgpregiymsck7e7aqa4s52zy}
cid_length=$(printf '%s' "$IPFS_CID" | LC_ALL=C wc -c | tr -d '[:space:]')
((cid_length <= 128)) || die "IPFS_CID przekracza limit 128 bajtów"
BATCH_NUMBER=${BATCH_NUMBER:-B-$(date -u +%Y)-0001}
[[ $BATCH_NUMBER =~ ^B-[0-9]{4}-[0-9]{4}$ ]] || die "BATCH_NUMBER musi mieć format B-RRRR-NNNN"
requested_address=${SUPPLY_CHAIN_ADDRESS:-}

AMOY_RPC_URL=${AMOY_RPC_URL:-https://polygon-amoy-bor-rpc.publicnode.com}
rpc=(--rpc-url "$AMOY_RPC_URL")
rpc_host=${AMOY_RPC_URL#*://} # w logach tylko host: ścieżka, query i login mogą zawierać token
rpc_host=${rpc_host%%[/?#]*}
rpc_host=${rpc_host##*@}
chain_id=$(cast chain-id "${rpc[@]}" 2>/dev/null) || die "RPC $rpc_host nie odpowiada (AMOY_RPC_URL)"
[[ $chain_id == "$CHAIN_ID" ]] || die "chainId $chain_id zamiast $CHAIN_ID - skrypt jest tylko dla Amoy"
chain_time=$(cast block latest -f timestamp "${rpc[@]}") || die "nie można odczytać czasu ostatniego bloku"
[[ $chain_time =~ ^[0-9]+$ ]] || die "RPC zwróciło nieprawidłowy czas bloku"
if [[ -n $requested_expiry ]] && decimal_le "$requested_expiry" "$chain_time"; then
  die "STATION_EXPIRES_AT musi być późniejsze niż czas ostatniego bloku ($chain_time)"
fi

base=$(cast base-fee "${rpc[@]}") || die "nie można odczytać base fee"
[[ $base =~ ^[0-9]+$ && ${#base} -le 18 ]] || die "base fee poza zakresem skryptu"
if [[ -n ${TIP_GWEI:-} ]]; then
  tip=$(cast to-wei "$TIP_GWEI" gwei 2>/dev/null) || die "TIP_GWEI nie jest liczbą: $TIP_GWEI"
  [[ $tip =~ ^[0-9]+$ && ${#tip} -le 18 ]] || die "TIP_GWEI poza zakresem skryptu"
  ((tip >= MIN_TIP_WEI)) || warn "tip $TIP_GWEI gwei jest poniżej 30 gwei - transakcja może utknąć"
else
  if ! suggested_hex=$(cast rpc eth_maxPriorityFeePerGas "${rpc[@]}" 2>/dev/null |
    jq -er 'strings | select(test("^0x[0-9a-fA-F]+$"))'); then
    warn "RPC nie podało poprawnego eth_maxPriorityFeePerGas; używam 30 gwei"
    suggested_hex=0x0
  fi
  suggested=$(cast to-dec "$suggested_hex") || die "nie można odczytać sugerowanego tipa"
  [[ $suggested =~ ^[0-9]+$ && ${#suggested} -le 18 ]] || die "sugerowany tip poza zakresem skryptu"
  tip=$((suggested > MIN_TIP_WEI ? suggested : MIN_TIP_WEI))
fi
# Sam --priority-gas-price nie wystarcza: Forge liczy maxFeePerGas z historii opłat i przerywa,
# gdy wyjdzie mniejszy od tipu. Dlatego skrypt podaje jawnie oba parametry.
max_fee=$((tip + 2 * base))

run_file="broadcast/DeploySupplyChain.s.sol/$CHAIN_ID/run-latest.json"
create_tx='[.transactions[] | select(.transactionType == "CREATE" and .contractName == "SupplyChain")][0]'
address=""
stale_code=0

# Jak w deploy-local.sh: ostatnie wdrożenie z broadcast/ musi istnieć na tym łańcuchu
# i pochodzić z bieżącego src/. Wołana w `if`, więc set -e tu nie działa: kroki sprawdzamy jawnie.
find_existing() {
  [[ -f $run_file ]] || return 1
  local tx addr input receipt receipt_addr code bytecode
  tx=$(jq -er "$create_tx.hash // empty" "$run_file") || return 1
  addr=$(jq -er "$create_tx.contractAddress // empty" "$run_file") || return 1
  input=$(jq -er "$create_tx.transaction.input // empty" "$run_file") || return 1
  [[ -n $tx && -n $addr && -n $input ]] || return 1
  receipt=$(cast rpc eth_getTransactionReceipt "$tx" "${rpc[@]}") || return 1
  [[ $(jq -r '.status // empty' <<<"$receipt") == 0x1 ]] || return 1
  receipt_addr=$(jq -r '.contractAddress // empty' <<<"$receipt") || return 1
  [[ -n $receipt_addr ]] && same "$receipt_addr" "$addr" || return 1
  code=$(cast code "$addr" "${rpc[@]}") || return 1
  [[ -n $code && $code != 0x ]] || return 1
  [[ $(cast call "$addr" "STAGE_COUNT()(uint32)" "${rpc[@]}") == 4 ]] || return 1
  forge build --quiet || die "kompilacja kontraktów nie powiodła się"
  bytecode=$(forge inspect SupplyChain bytecode) || die "forge inspect nie zwrócił bytecode"
  [[ $bytecode == 0x?* ]] || die "forge inspect zwrócił pusty bytecode"
  if [[ $(lower "$input") != "$(lower "$bytecode")"* ]]; then
    stale_code=1
    return 1
  fi
  address=$(cast to-check-sum-address "$addr")
}

batch_id=$(cast keccak "$BATCH_NUMBER")
station_state() {
  cast call "$address" "getStation(address)(bool,bytes32,uint64,uint32)" "$station" "${rpc[@]}" |
    awk '{ print $1 }' | tr '\n' ' '
}
batch_state() {
  cast call "$address" "getBatch(bytes32)(bool,uint32,bool)" "$batch_id" "${rpc[@]}" |
    awk '{ print $1 }' | tr '\n' ' '
}
has_role() {
  local result
  result=$(cast call "$address" "hasRole(bytes32,address)(bool)" "$1" "$2" "${rpc[@]}") ||
    die "nie można sprawdzić roli konta $2 w RPC"
  [[ $result == true || $result == false ]] || die "nieprawidłowa odpowiedź hasRole"
  [[ $result == true ]]
}
station_matches() {
  local state active id expires mask now
  state=$(station_state) || die "nie można odczytać rejestru stacji z RPC"
  read -r active id expires mask <<<"$state"
  [[ $active =~ ^(true|false)$ && $id =~ ^0x[0-9a-fA-F]{64}$ &&
    $expires =~ ^[0-9]+$ && $mask =~ ^[0-9]+$ ]] ||
    die "nieprawidłowa odpowiedź getStation"
  [[ $active == true && $mask == "$STATION_STAGE_MASK" ]] && same "$id" "$STATION_ID" || return 1
  now=$(cast block latest -f timestamp "${rpc[@]}") || die "nie można odczytać czasu ostatniego bloku"
  [[ $now =~ ^[0-9]+$ ]] || die "RPC zwróciło nieprawidłowy czas bloku"
  decimal_le "$expires" "$now" && return 1
  [[ -z $requested_expiry || $requested_expiry == "$expires" ]]
}
batch_started() {
  local state exists next
  state=$(batch_state) || die "nie można odczytać partii z RPC"
  read -r exists next _ <<<"$state"
  [[ $exists =~ ^(true|false)$ && $next =~ ^[0-9]+$ ]] || die "nieprawidłowa odpowiedź getBatch"
  [[ $exists == true ]] && ((next >= 1))
}
eventually() { # publiczny RPC rozdziela zapytania na węzły, które bywają o blok w tyle
  local try
  for try in 1 2 3 4 5 6 7 8 9 10; do
    if "$@"; then return 0; fi
    if ((try < 10)); then sleep 3; fi
  done
  return 1
}

do_deploy=1
do_register=1
do_smoke=1
if [[ $fresh == 0 && -f $run_file ]]; then
  if ! find_existing; then
    if [[ $stale_code == 1 ]]; then
      die "SupplyChain w src/ różni się od wdrożonego na Amoy - nowy kontrakt: --fresh"
    fi
    die "nie można potwierdzić ostatniego wdrożenia z $run_file; sprawdź RPC i receipt. \
Nowy kontrakt tylko świadomie: --fresh"
  fi
  do_deploy=0
  if station_matches; then do_register=0; fi
  state=$(batch_state) || die "nie można odczytać partii z RPC"
  read -r b_exists b_next _ <<<"$state"
  [[ $b_exists =~ ^(true|false)$ && $b_next =~ ^[0-9]+$ ]] || die "nieprawidłowa odpowiedź getBatch"
  if [[ $b_exists == true ]]; then
    ((b_next >= 1)) || die "partia $BATCH_NUMBER istnieje bez etapu 0 - ustaw nowy BATCH_NUMBER"
    do_smoke=0
  fi
  if [[ $do_register == 1 ]] && ! has_role "$ADMIN_ROLE" "$admin"; then
    die "$admin nie ma DEFAULT_ADMIN_ROLE w $address - nowy kontrakt: --fresh"
  fi
  if [[ $do_smoke == 1 ]]; then
    relayer_role=$(cast call "$address" "RELAYER_ROLE()(bytes32)" "${rpc[@]}") ||
      die "nie można odczytać RELAYER_ROLE z RPC"
    [[ $relayer_role =~ ^0x[0-9a-fA-F]{64}$ ]] || die "nieprawidłowa odpowiedź RELAYER_ROLE"
    if ! has_role "$relayer_role" "$relayer"; then
      die "$relayer nie ma RELAYER_ROLE w $address - nowy kontrakt: --fresh"
    fi
  fi
elif [[ $fresh == 0 && -f $root/shared/deployments/amoy.json ]]; then
  die "istnieje shared/deployments/amoy.json, ale brakuje $run_file; odzyskaj broadcast \
albo świadomie uruchom --fresh"
fi
if [[ -n $requested_address ]] && { [[ $do_deploy == 1 ]] || ! same "$requested_address" "$address"; }; then
  die "SUPPLY_CHAIN_ADDRESS=$requested_address nie jest ostatnim wdrożeniem z broadcast/ - \
skrypt ustawia adres sam, zostaw SUPPLY_CHAIN_ADDRESS puste"
fi

if [[ $do_smoke == 1 ]]; then
  window=$MAX_DEADLINE_WINDOW
  if [[ $do_deploy == 0 ]]; then
    window=$(cast call "$address" "maxDeadlineWindow()(uint256)" "${rpc[@]}" | awk '{ print $1 }') ||
      die "nie można odczytać maxDeadlineWindow"
    [[ $window =~ ^[0-9]+$ ]] || die "nieprawidłowa odpowiedź maxDeadlineWindow"
  fi
  decimal_le "$DEADLINE_SECONDS" "$window" ||
    die "DEADLINE_SECONDS przekracza maxDeadlineWindow ($window s)"
fi

deploy_gas=$((GAS_DEPLOY + GAS_GRANT))
same "$qa" "$ZERO_ADDRESS" || deploy_gas=$((deploy_gas + GAS_GRANT))
same "$admin_cfg" "$deployer" || deploy_gas=$((deploy_gas + 2 * GAS_GRANT)) # przekazanie admina
needs=""
steps=""
if [[ $do_deploy == 1 ]]; then needs+="$deployer $deploy_gas"$'\n' steps+=" DeploySupplyChain"; fi
if [[ $do_register == 1 ]]; then needs+="$admin $GAS_REGISTER"$'\n' steps+=" RegisterStation"; fi
if [[ $do_smoke == 1 ]]; then needs+="$relayer $GAS_SMOKE"$'\n' steps+=" SmokeStage($BATCH_NUMBER)"; fi

info "Amoy przez $rpc_host: base fee $base wei, tip $(gwei "$tip") gwei, maxFeePerGas $(gwei "$max_fee") gwei"
info "Deployer $deployer, admin $admin, relayer $relayer, stacja $station"
if [[ $do_deploy == 0 ]]; then info "Kontrakt z broadcast/: $address"; fi
info "Do wykonania:${steps:- nic, smoke jest już na łańcuchu}"
while read -r who gas; do
  [[ -n $who ]] || continue
  balance=$(cast balance "$who" --ether "${rpc[@]}")
  required=$(awk -v g="$gas" -v f="$max_fee" 'BEGIN {
    scaled = g * 1.3 * f / 1e10; rounded = int(scaled)
    if (rounded < scaled) rounded++
    printf "%.8f", rounded / 1e8
  }')
  info "Saldo $who: $balance POL, szacowany wymagany zapas $required POL"
  awk -v b="$balance" -v r="$required" 'BEGIN { exit !(b >= r) }' ||
    die "za mało POL na $who (faucet: https://faucet.polygon.technology)"
done <<<"$(printf '%s' "$needs" |
  awk '{ k = tolower($1); gas[k] += $2; name[k] = $1 } END { for (k in gas) print name[k], gas[k] }')"

# Wszystkie zmienne skryptów Forge jawnie: wartości z .env nie nadpisują zmiennych procesu.
export EXPECTED_CHAIN_ID=$CHAIN_ID DEPLOYER_PRIVATE_KEY ADMIN_PRIVATE_KEY RELAYER_PRIVATE_KEY \
  STATION_PRIVATE_KEY RELAYER_ADDRESS="$relayer_cfg" QA_INSPECTOR_ADDRESS="$qa" \
  ADMIN_ADDRESS="$admin_cfg" MAX_DEADLINE_WINDOW \
  SUPPLY_CHAIN_ADDRESS="$address" STATION_ADDRESS="$station" STATION_ID STATION_STAGE_MASK \
  STATION_EXPIRES_AT="${requested_expiry:-$((chain_time + 365 * 86400))}" BATCH_NUMBER \
  DEADLINE_SECONDS IPFS_CID

forge_step() {
  local args=(script "script/$1.s.sol" "${rpc[@]}" --slow --priority-gas-price "$tip"
    --with-gas-price "$max_fee")
  if [[ $dry_run == 0 ]]; then args+=(--broadcast); fi
  forge "${args[@]}" || die "$1 nie powiódł się - sprawdź receipt i broadcast przed ponowieniem"
}

if [[ $dry_run == 1 ]]; then
  first=${steps# }
  first=${first%% *}
  first=${first%%(*}
  if [[ -n $first ]]; then
    info "Symulacja $first.s.sol na stanie Amoy (bez --broadcast)"
    forge_step "$first"
  fi
  info "Dry run zakończony, nic nie wysłano. Prawdziwy smoke: ten sam skrypt bez --dry-run"
  exit 0
fi

payers=$(printf '%s\n' "$deployer" "$admin" "$relayer" | tr '[:upper:]' '[:lower:]' | sort -u)
total_balance() {
  local a
  for a in $payers; do cast balance "$a" --ether "${rpc[@]}"; done |
    awk '{ s += $1 } END { printf "%.6f", s }'
}
before=$(total_balance)

if [[ $do_deploy == 1 ]]; then
  info "1/4 DeploySupplyChain"
  forge_step DeploySupplyChain
  address=$(cast to-check-sum-address "$(jq -r "$create_tx.contractAddress" "$run_file")")
  export SUPPLY_CHAIN_ADDRESS=$address
else
  info "1/4 DeploySupplyChain pominięty: kontrakt $address już działa"
fi
if [[ $do_register == 1 ]]; then
  info "2/4 RegisterStation ($station)"
  forge_step RegisterStation
else
  info "2/4 RegisterStation pominięty: stacja aktywna z tym STATION_ID i maską"
fi
eventually station_matches ||
  die "rejestr w $address nie ma stacji $station z oczekiwanym STATION_ID i maską"
if [[ $do_smoke == 1 ]]; then
  info "3/4 SmokeStage ($BATCH_NUMBER, etap 0)"
  forge_step SmokeStage
else
  info "3/4 SmokeStage pominięty: partia $BATCH_NUMBER ma już etap 0"
fi
eventually batch_started || die "partia $BATCH_NUMBER nie ma etapu 0 w $address"

info "4/4 export-deployment.sh $CHAIN_ID amoy"
script/export-deployment.sh "$CHAIN_ID" amoy
if command -v git >/dev/null && ! git -C "$root" diff --quiet -- shared/abi/SupplyChain.abi.json; then
  warn "shared/abi/SupplyChain.abi.json się zmienił - commit razem z kontraktem, powiadom backend"
fi

deployment="$root/shared/deployments/amoy.json"
deployed=$(jq -r .address "$deployment")
smoke_file="broadcast/SmokeStage.s.sol/$CHAIN_ID/run-latest.json"
smoke_tx() { # transakcja $1 tej partii (pierwszy argument $2) do tego kontraktu z broadcast/
  [[ -f $smoke_file ]] || return 0
  jq -r --arg to "$(lower "$deployed")" --arg fn "$1(" --arg arg0 "$(lower "$2")" '[.transactions[]
    | select((.transaction.to // "" | ascii_downcase) == $to and (.function // "" | startswith($fn))
      and ((.arguments // [])[0] // "" | ascii_downcase) == $arg0)] | last | .hash // empty' "$smoke_file"
}
batch_tx=$(smoke_tx createBatch "$BATCH_NUMBER")
stage_tx=$(smoke_tx completeStage "$batch_id")
if [[ $do_smoke == 1 && ( -z $batch_tx || -z $stage_tx ) ]]; then
  die "brak hashy transakcji smoke w $smoke_file - sprawdź broadcast i stan partii"
fi
stage_note=""
finality=""
if [[ -n $stage_tx ]]; then
  receipt=$(cast receipt "$stage_tx" --json "${rpc[@]}")
  [[ $(jq -r .status <<<"$receipt") == 0x1 ]] || die "transakcja completeStage nie powiodła się"
  stage_block=$(($(jq -r .blockNumber <<<"$receipt")))
  stage_hash=$(jq -r .blockHash <<<"$receipt")
  block_base=$(cast block "$stage_block" -f baseFeePerGas "${rpc[@]}")
  paid_tip=$(($(jq -r .effectiveGasPrice <<<"$receipt") - block_base))
  stage_note=" (blok $stage_block, tip $(gwei "$paid_tip") gwei)"
  if [[ $do_smoke == 1 ]]; then
    info "Czekam, aż blok $stage_block będzie finalized (maks. 120 s)"
    fin=0
    for _ in $(seq 1 60); do
      fin=$(cast block finalized -f number "${rpc[@]}" 2>/dev/null) || fin=0
      if ((fin >= stage_block)); then break; fi
      sleep 2
    done
    if ((fin < stage_block)); then
      finality="blok $stage_block nie był finalized po 120 s"
      warn "$finality"
    elif [[ $(cast block "$stage_block" -f hash "${rpc[@]}") != "$stage_hash" ]]; then
      die "blok $stage_block zmienił hash przed finalized (reorg) - sprawdź completeStage"
    else
      stamp=$(cast block "$stage_block" -f timestamp "${rpc[@]}")
      finality="finalized najpóźniej $(($(date +%s) - stamp)) s po bloku $stage_block"
    fi
  fi
fi
spent=$(awk -v a="$before" -v b="$(total_balance)" 'BEGIN { printf "%.6f", a - b }')

echo
echo "Gotowe: SupplyChain $deployed na Amoy (blok wdrożenia $(jq -r .deploymentBlock "$deployment"))"
echo "  kontrakt       $EXPLORER/address/$deployed"
echo "  wdrożenie      $EXPLORER/tx/$(jq -r .deploymentTxHash "$deployment")"
if [[ -n $batch_tx ]]; then echo "  createBatch    $EXPLORER/tx/$batch_tx"; fi
if [[ -n $stage_tx ]]; then echo "  completeStage  $EXPLORER/tx/$stage_tx$stage_note"; fi
if [[ -n $finality ]]; then echo "  finalność      $finality"; fi
if [[ -n $steps ]]; then echo "  koszt          $spent POL (różnica sald kont)"; fi
echo "Wpisz te dane i faucet do docs/weekly_plans/week1/adam-amoy-notes.md."
echo "Publiczne dane: shared/deployments/amoy.json i ewentualnie zmienione ABI."
echo "Klucze zostają w $env_file; broadcast/ i cache/ pozostają lokalne."
