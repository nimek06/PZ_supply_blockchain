#!/usr/bin/env bash
# Jednym poleceniem: stos -> kontrakty -> stacja -> backend -> symulator.
set -euo pipefail

source "$(dirname "${BASH_SOURCE[0]}")/common.sh"

export E2E_WORKDIR STATION_KEY STATION_CERT BACKEND_LOG BACKEND_PID_FILE

require_tools docker java python3 jq curl shasum

info "E2E workdir: $E2E_WORKDIR"

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
trap stop_backend EXIT

"$(dirname "${BASH_SOURCE[0]}")/setup-stack.sh"
"$(dirname "${BASH_SOURCE[0]}")/deploy-contracts.sh"
"$(dirname "${BASH_SOURCE[0]}")/start-backend.sh"
"$(dirname "${BASH_SOURCE[0]}")/prepare-station.sh"
"$(dirname "${BASH_SOURCE[0]}")/run-simulator.sh"

info "E2E zakończone pomyślnie"
info "Logi backendu: $BACKEND_LOG"
info "Pliki tymczasowe: $E2E_WORKDIR"
