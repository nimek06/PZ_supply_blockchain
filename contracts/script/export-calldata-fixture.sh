#!/usr/bin/env bash
# Generates ../shared/fixtures/complete-stage-calldata.json: inputs of completeStage(...) and the
# expected calldata, encoded independently with `cast calldata` (alloy ABI encoder).
# Solidity (contracts/test/CompleteStageCalldata.t.sol) and Java (CompleteStageCallEncoderTest) must
# produce exactly these bytes.
# Usage (from contracts/): script/export-calldata-fixture.sh [--check]
#   --check  regenerate in memory and fail if the committed fixture differs (no file is written).
# Requires: cast, jq. Reads ../shared/fixtures/signature-vector.json (owned by the simulator).
#
# The station is Anvil #1: a PUBLIC development key, test-only, never fund it on Amoy.
# Signatures are deterministic (RFC 6979), so the output is reproducible byte for byte.
set -euo pipefail

mode="${1:-write}"
[[ "$mode" == "write" || "$mode" == "--check" ]] || { echo "usage: $0 [--check]" >&2; exit 2; }
cd "$(dirname "$0")/.."
for tool in cast jq; do
  command -v "$tool" >/dev/null || { echo "missing tool: $tool" >&2; exit 1; }
done

vector="../shared/fixtures/signature-vector.json"
output="../shared/fixtures/complete-stage-calldata.json"
[[ -f "$vector" ]] || { echo "missing $vector" >&2; exit 1; }

readonly STATION_KEY="0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d"
readonly FUNCTION_SIGNATURE="completeStage(bytes32,uint32,bytes32,bool,uint256,string,bytes,uint256,bytes)"
readonly CID_V1="bafkreigh2akiscaildcqabsyg3dfr6chu3fgpregiymsck7e7aqa4s52zy"
readonly CID_V0="QmYwAPJzv5CZsnA625s3Xf2nemtYgPpHdWEz79ojWnPbdG"
CID_MAX="bafy$(printf 'a%.0s' $(seq 1 124))"
readonly CID_MAX

batch_id=$(jq -r '.report.batchId' "$vector")
deadline=$(jq -r '.stageCompletion.message.deadline' "$vector")

sign_completion() { # stageId reportHash qaPassed
  local typed_data
  typed_data=$(jq -c --argjson stage "$1" --arg hash "$2" --argjson passed "$3" '{
      types: .stageCompletion.types,
      primaryType: "StageCompletion",
      domain: {name: .domain.name, version: .domain.version, chainId: .domain.chainId,
               verifyingContract: .domain.verifyingContract},
      message: (.stageCompletion.message | .stageId = $stage | .reportHash = $hash | .qaPassed = $passed)
    }' "$vector")
  cast wallet sign --data --private-key "$STATION_KEY" "$typed_data"
}

# The independent signer must reproduce the shared vector, otherwise the fixture is not trustworthy.
vector_signer=$(jq -r '.stageCompletion.signer' "$vector")
[[ "$(cast wallet address --private-key "$STATION_KEY" | tr 'A-F' 'a-f')" == "$(tr 'A-F' 'a-f' <<<"$vector_signer")" ]] \
  || { echo "station key does not match the signer of $vector" >&2; exit 1; }
vector_stage=$(jq -r '.stageCompletion.message.stageId' "$vector")
vector_hash=$(jq -r '.stageCompletion.message.reportHash' "$vector")
vector_passed=$(jq -r '.stageCompletion.message.qaPassed' "$vector")
[[ "$(sign_completion "$vector_stage" "$vector_hash" "$vector_passed")" == "$(jq -r '.stageCompletion.signature' "$vector")" ]] \
  || { echo "cast signature differs from $vector" >&2; exit 1; }

# case <name> <description> <stageId> <qaPassed> <reportHash> <cid> <stationSig> <approvalDeadline> <approvalSig>
build_case() {
  local calldata
  calldata=$(cast calldata "$FUNCTION_SIGNATURE" "$batch_id" "$3" "$5" "$4" "$deadline" "$6" "$7" "$8" "$9")
  jq -n --arg name "$1" --arg description "$2" --arg batchId "$batch_id" --argjson stageId "$3" \
    --argjson qaPassed "$4" --arg reportHash "$5" --argjson deadline "$deadline" --arg ipfsCid "$6" \
    --arg stationSignature "$7" --argjson approvalDeadline "$8" --arg approvalSignature "$9" \
    --arg expectedCalldata "$calldata" '{
      name: $name,
      description: $description,
      input: {batchId: $batchId, stageId: $stageId, reportHash: $reportHash, qaPassed: $qaPassed,
              deadline: $deadline, ipfsCid: $ipfsCid, stationSignature: $stationSignature,
              approvalDeadline: $approvalDeadline, approvalSignature: $approvalSignature},
      expectedCalldata: $expectedCalldata
    }'
}

station_case() { # name description stageId qaPassed label cid
  local report_hash
  report_hash=$(cast keccak "calldata-fixture/$5")
  build_case "$1" "$2" "$3" "$4" "$report_hash" "$6" "$(sign_completion "$3" "$report_hash" "$4")" 0 "0x"
}

cases='[]'
append_case() { cases=$(jq -c --argjson c "$1" '. + [$c]' <<<"$cases"); }

# Order matters: the Foundry test replays the cases on one batch (0, 1, rejected 2, approved 2, 3).
case_json=$(station_case "stage0-station-only" \
  "Etap 0: tylko podpis stacji; approvalDeadline=0 i pusty approvalSignature (bytes o długości 0)." \
  0 true stage0 "$CID_V1")
append_case "$case_json"
case_json=$(station_case "stage1-station-only" \
  "Etap 1: CIDv0 (46 znaków) i pusty blok zatwierdzenia." \
  1 true stage1 "$CID_V0")
append_case "$case_json"
case_json=$(station_case "stage2-qa-rejected" \
  "Etap 2 z qaPassed=false: odrzucenie (StageRejected) bez zatwierdzenia operatora, partia nie przesuwa się." \
  2 false stage2-rejected "$CID_V1")
append_case "$case_json"
case_json=$(build_case "stage2-qa-approved" \
  "Etap 2 z qaPassed=true: prawdziwe podpisy stacji i operatora zmiany z signature-vector.json." \
  "$vector_stage" "$vector_passed" "$vector_hash" "$CID_V1" \
  "$(jq -r '.stageCompletion.signature' "$vector")" \
  "$(jq -r '.stageApproval.message.deadline' "$vector")" \
  "$(jq -r '.stageApproval.signature' "$vector")")
append_case "$case_json"
case_json=$(station_case "stage3-max-cid" \
  "Etap 3: ipfsCid o maksymalnej długości 128 bajtów (MAX_CID_LENGTH) - wartość graniczna, nie prawdziwy CID." \
  3 true stage3 "$CID_MAX")
append_case "$case_json"

render() {
  jq -n --argjson cases "$cases" --slurpfile vector "$vector" --arg signature "$FUNCTION_SIGNATURE" \
    --arg selector "$(cast sig "$FUNCTION_SIGNATURE")" '{
      fixtureVersion: 1,
      description: "Fixture zgodności ABI (tydzień 2): wejścia completeStage(...) i oczekiwane bajty calldata zakodowane niezależnie przez `cast calldata`. Kodowanie Solidity (abi.encodeCall) i Java (CompleteStageCallEncoder) muszą dać identyczne bajty. Przypadki to kolejne kroki jednej partii (batchNumber z signature-vector.json): etapy 0 i 1, odrzucenie kontroli, zatwierdzona kontrola (wektor wspólny) i etap 3; test Foundry wykonuje je w tej kolejności na kontrakcie. Podpisy stacji składa publiczny klucz deweloperski Anvil #1 - nie zasilaj tego adresu na Amoy. Plik odtwarza contracts/script/export-calldata-fixture.sh (--check sprawdza zgodność); nie edytuj go ręcznie.",
      function: $signature,
      selector: $selector,
      batchNumber: $vector[0].batchNumber,
      domain: $vector[0].domain,
      stationAddress: $vector[0].stageCompletion.signer,
      qaInspectorAddress: $vector[0].stageApproval.signer,
      cases: $cases
    }'
}

rendered=$(render)
if [[ "$mode" == "--check" ]]; then
  [[ -f "$output" && "$rendered" == "$(<"$output")" ]] \
    || { echo "$output is stale - run script/export-calldata-fixture.sh" >&2; exit 1; }
  echo "$output is up to date"
else
  printf '%s\n' "$rendered" > "$output"
  echo "Fixture -> shared/fixtures/complete-stage-calldata.json ($(jq '.cases | length' "$output") cases)"
fi
