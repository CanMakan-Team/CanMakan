#!/bin/bash
# macOS/zsh equivalent of run-full-stack.ps1. Opens backend, web, and mobile
# each in their own Terminal.app window (mirroring Start-Process on Windows)
# and tracks their PIDs so Stop FullStack (mac) can clean them up later.
#
# Why separate Terminal windows instead of background jobs: VS Code tears
# down a shell task's whole process group once the task's own command exits,
# which kills background/nohup'd children too since they're still part of
# that group. A process launched in its own Terminal.app window is a fully
# independent process tree, so it survives the launching task finishing.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WORKSPACE="$(dirname "$SCRIPT_DIR")"
PID_FILE="$SCRIPT_DIR/fullstack.pids"

BACKEND_DIR="$WORKSPACE/server/backend"
WEB_DIR="$WORKSPACE/client/web"
MOBILE_DIR="$WORKSPACE/client/mobile"

# Clear stale PID tracking from a previous launch.
: > "$PID_FILE"

start_tracked_window() {
    local working_dir="$1"
    local label="$2"
    shift 2

    # Build a small launch script rather than inlining the command into the
    # AppleScript string: it sidesteps AppleScript/shell quoting entirely.
    # It records its own PID ($$) before "exec" replaces it with the real
    # command, so the PID we track stays valid through the process's whole
    # life the same way it would for ./mvnw or ./gradlew re-execing java.
    local launch_script
    launch_script="$(mktemp "${TMPDIR:-/tmp}/canmakan-${label}-XXXXXX.sh")"
    {
        echo "#!/bin/bash"
        echo "rm -f $(printf '%q' "$launch_script")"
        echo "cd $(printf '%q' "$working_dir")"
        echo "echo \"\$\$|${label}\" >> $(printf '%q' "$PID_FILE")"
        printf 'exec'
        for arg in "$@"; do
            printf ' %q' "$arg"
        done
        echo
    } > "$launch_script"
    chmod +x "$launch_script"

    echo "Starting $label in a new Terminal window..."
    osascript -e "tell application \"Terminal\" to do script \"$launch_script\"" >/dev/null
}

start_tracked_window "$BACKEND_DIR" "backend" ./mvnw spring-boot:run -Dspring-boot.run.profiles=local
start_tracked_window "$WEB_DIR" "web" npm run dev
start_tracked_window "$MOBILE_DIR" "mobile" ./gradlew assembleDebug

echo "Full stack launch started. Tracked PIDs: $PID_FILE"
echo "(macOS may prompt once for permission to let Terminal be controlled — allow it.)"
