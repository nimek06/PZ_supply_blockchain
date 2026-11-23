#!/usr/bin/env bash
# Jednym poleceniem: stos -> kontrakty -> backend -> stacja -> symulator.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

usage() {
  cat <<'HELP'
Lokalny przebieg end-to-end Supply Blockchain.

Użycie:
  ./backend/scripts/local-e2e/run-e2e.sh

Opcje:
  -h, --help  Pokaż pomoc

Runner resetuje lokalny łańcuch Anvil i bazy supplychain_e2e oraz
supplychain_e2e_test. Wymaga Dockera, JDK 21, Pythona 3, jq, curl i shasum.
Kolory można wyłączyć przez ustawienie NO_COLOR=1.
HELP
}

case "${1:-}" in
  -h | --help)
    usage
    exit 0
    ;;
  "")
    ;;
  *)
    usage >&2
    exit 2
    ;;
esac

source "$SCRIPT_DIR/common.sh"

export E2E_WORKDIR STATION_KEY STATION_CERT BACKEND_LOG BACKEND_PID_FILE

require_tools docker java python3 jq curl shasum

current_step="przygotowanie"
step_number=0
readonly TOTAL_STEPS=5

printf '\n%s========================================%s\n' "$COLOR_INFO" "$COLOR_RESET"
printf '  Lokalny przebieg E2E Supply Blockchain\n'
printf '%s========================================%s\n' "$COLOR_INFO" "$COLOR_RESET"
info "Artefakty i logi: $E2E_WORKDIR"
warn "Anvil zostanie zresetowany; odtworzone będą wyłącznie bazy E2E."

run_step() {
  local name=$1
  shift
  step_number=$((step_number + 1))
  current_step=$name
  local started_at=$SECONDS

  printf '\n%s[%d/%d] %s%s\n' "$COLOR_INFO" "$step_number" "$TOTAL_STEPS" "$name" "$COLOR_RESET"
  if "$@"; then
    printf '%s[OK] %s (%ds)%s\n' "$COLOR_SUCCESS" "$name" "$((SECONDS - started_at))" "$COLOR_RESET"
  else
    local exit_code=$?
    printf '%s[FAIL] %s (kod %d)%s\n' "$COLOR_ERROR" "$name" "$exit_code" "$COLOR_RESET" >&2
    return "$exit_code"
  fi
}

stop_backend() {
  if [[ -f "$BACKEND_PID_FILE" ]]; then
    local pid
    pid=$(cat "$BACKEND_PID_FILE")
    if kill -0 "$pid" 2>/dev/null; then
      info "Zatrzymuję backend (pid $pid)"
      kill "$pid" 2>/dev/null || true
      wait "$pid" 2>/dev/null || true
    fi
    rm -f "$BACKEND_PID_FILE"
  fi
}

finish() {
  local exit_code=$?
  trap - EXIT
  stop_backend

  if ((exit_code == 0)); then
    printf '\n%sE2E zakończone pomyślnie.%s\n' "$COLOR_SUCCESS" "$COLOR_RESET"
  else
    printf '\n%sE2E przerwane na kroku: %s.%s\n' "$COLOR_ERROR" "$current_step" "$COLOR_RESET" >&2
  fi
  printf 'Log backendu: %s\n' "$BACKEND_LOG"
  printf 'Usługi Docker pozostają uruchomione. Zatrzymanie bez usuwania danych:\n'
  printf '  cd infrastructure/local && docker compose down\n'
  exit "$exit_code"
}
trap finish EXIT

run_step "Uruchomienie PostgreSQL, Anvila i Kubo" "$SCRIPT_DIR/setup-stack.sh"
run_step "Wdrożenie kontraktu i ról" "$SCRIPT_DIR/deploy-contracts.sh"
run_step "Budowa i uruchomienie backendu" "$SCRIPT_DIR/start-backend.sh"
run_step "Przygotowanie certyfikatu i stacji" "$SCRIPT_DIR/prepare-station.sh"
run_step "Scenariusz symulatora" "$SCRIPT_DIR/run-simulator.sh"
