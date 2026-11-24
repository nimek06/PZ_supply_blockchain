#!/usr/bin/env bash
# Tworzy partię przez API jako operator: SIWE -> token Bearer -> POST /batches.
#
# To narzędzie pomocnicze do lokalnego E2E po stronie OPERATORA. Symulator pozostaje
# stacją i nie tworzy partii (docs/rules/access-control.md: "Osoba 4: login skryp-
# towy i testy"). Na stdout wypisuje sam numer partii (B-RRRR-NNNN), a szczegóły
# na stderr - dzięki temu wynik da się przekazać do `--batch-number`.
#
# Wymagania: curl, jq, cast (Foundry), uruchomiony backend.
# Klucz domyślny to publiczny klucz deweloperski Anvil #3 (rola OPERATOR_ROLE);
# nigdy nie używaj go poza lokalnym łańcuchem. Nadpisz przez OPERATOR_PRIVATE_KEY
# albo OPERATOR_KEY_ENV (nazwa zmiennej ze kluczem).
#
# Użycie:
#   BACKEND_URL=http://127.0.0.1:8080 ./create-batch.sh
#   OPERATOR_KEY_ENV=OPERATOR_PRIVATE_KEY ./create-batch.sh
set -euo pipefail

BACKEND_URL="${BACKEND_URL:-http://127.0.0.1:8080}"
OPERATOR_PRIVATE_KEY="${OPERATOR_PRIVATE_KEY:-0x7c852118294e51e653712a81e05800f419141751be58f605c371e15141b007a6}"
if [[ -n "${OPERATOR_KEY_ENV:-}" ]]; then
  OPERATOR_PRIVATE_KEY="${!OPERATOR_KEY_ENV:-}"
fi
IDEMPOTENCY_KEY="${IDEMPOTENCY_KEY:-operator-$(date +%s)}"

die() { echo "error: $*" >&2; exit 1; }
info() { echo "==> $*" >&2; }

command -v curl >/dev/null 2>&1 || die "brak curl"
command -v jq >/dev/null 2>&1 || die "brak jq"
command -v cast >/dev/null 2>&1 || die "brak cast (Foundry)"
[[ -n "$OPERATOR_PRIVATE_KEY" ]] || die "ustaw OPERATOR_PRIVATE_KEY albo OPERATOR_KEY_ENV"

address=$(cast wallet address --private-key "$OPERATOR_PRIVATE_KEY")
info "operator $address, backend $BACKEND_URL"

challenge=$(curl -fsS -X POST "$BACKEND_URL/api/v1/auth/challenges" \
  -H 'content-type: application/json' -d "$(jq -nc --arg a "$address" '{address:$a}')")
challenge_id=$(jq -er '.challengeId' <<<"$challenge")
message=$(jq -er '.message' <<<"$challenge")

# SIWE podpisujemy EIP-191 (personal_sign), tak samo jak portfel operatora.
signature=$(cast wallet sign --private-key "$OPERATOR_PRIVATE_KEY" "$message")

session=$(curl -fsS -X POST "$BACKEND_URL/api/v1/auth/sessions" \
  -H 'content-type: application/json' \
  -d "$(jq -nc --arg c "$challenge_id" --arg m "$message" --arg s "$signature" \
        '{challengeId:$c,message:$m,signature:$s}')")
token=$(jq -er '.token' <<<"$session")
info "sesja operatora OK"

created=$(curl -fsS -X POST "$BACKEND_URL/api/v1/batches" \
  -H "authorization: Bearer $token" \
  -H "idempotency-key: $IDEMPOTENCY_KEY" \
  -H 'content-type: application/json' -d '{}')

batch_number=$(jq -er '.batchNumber' <<<"$created")
batch_id=$(jq -er '.batchId' <<<"$created")
command_id=$(jq -er '.commandId' <<<"$created")
info "polecenie $command_id, batchId $batch_id (idempotency-key $IDEMPOTENCY_KEY)"

echo "$batch_number"
