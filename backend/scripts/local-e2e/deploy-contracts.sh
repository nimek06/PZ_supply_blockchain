#!/usr/bin/env bash
# Wdrożenie kontraktu SupplyChain na lokalnym Anvilu.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

require_tools docker jq shasum

info "Wdrażam kontrakty (deploy-local.sh --docker)"
cd "$CONTRACTS_DIR"
./script/deploy-local.sh --docker

address=$(contract_address)
[[ ${address,,} == ${CANONICAL_CONTRACT,,} ]] ||
  die "kontrakt $address != kanoniczny $CANONICAL_CONTRACT — Anvil nie był pusty? (patrz setup-stack.sh)"
info "Kontrakt: $address"
