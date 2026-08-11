#!/bin/bash
# macOS/zsh equivalent of stop-process.ps1. Stops backend/web/mobile/fullstack
# processes started via the "(mac)" run tasks.
set -uo pipefail

TARGET="${1:-}"
case "$TARGET" in
    backend|web|mobile|fullstack) ;;
    *)
        echo "Usage: $0 {backend|web|mobile|fullstack}" >&2
        exit 1
        ;;
esac

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PID_FILE="$SCRIPT_DIR/fullstack.pids"

# Kills a process and its children (mirrors "taskkill /T" on Windows).
kill_process_tree() {
    local pid="$1"
    if [ -z "$pid" ] || ! [ "$pid" -gt 0 ] 2>/dev/null; then
        return
    fi
    local child
    for child in $(pgrep -P "$pid" 2>/dev/null); do
        kill_process_tree "$child"
    done
    kill -9 "$pid" 2>/dev/null || true
}

stop_listeners_on_port() {
    local port="$1"
    local pid
    for pid in $(lsof -ti tcp:"$port" -sTCP:LISTEN 2>/dev/null); do
        echo "Stopping listener PID $pid on port $port..."
        kill_process_tree "$pid"
    done
}

stop_by_command_match() {
    local pattern="$1"
    local pid
    for pid in $(pgrep -f "$pattern" 2>/dev/null); do
        # Don't kill this script's own shell if the pattern is broad enough to match it.
        [ "$pid" = "$$" ] && continue
        echo "Stopping PID $pid matching '$pattern'..."
        kill_process_tree "$pid"
    done
}

stop_tracked_fullstack() {
    if [ ! -f "$PID_FILE" ]; then
        return
    fi
    while IFS='|' read -r pid label; do
        [ -z "$pid" ] && continue
        echo "Stopping tracked ${label:-process} PID $pid..."
        kill_process_tree "$pid"
    done < "$PID_FILE"
    rm -f "$PID_FILE"
}

case "$TARGET" in
    backend)
        stop_by_command_match 'spring-boot:run|BackendApplication'
        stop_listeners_on_port 8080
        ;;
    web)
        stop_by_command_match 'npm run dev|node_modules/.bin/vite|vite/bin/vite.js'
        stop_listeners_on_port 5173
        stop_listeners_on_port 4173
        ;;
    mobile)
        stop_by_command_match 'assembleDebug'
        ;;
    fullstack)
        stop_tracked_fullstack
        # Fallback: catch orphans if the PID file is missing or children re-parented.
        stop_by_command_match 'spring-boot:run|BackendApplication|npm run dev|node_modules/.bin/vite|vite/bin/vite.js|assembleDebug'
        stop_listeners_on_port 8080
        stop_listeners_on_port 5173
        stop_listeners_on_port 4173
        ;;
esac

echo "Stopped $TARGET processes."
