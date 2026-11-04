#!/usr/bin/env bash
set -euo pipefail

export DISPLAY="${DISPLAY:-:99}"
Xvfb "$DISPLAY" -screen 0 1920x1080x24 -ac +extension GLX +render -noreset &
XVFB_PID=$!
for attempt in {1..20}; do
  [[ -S "/tmp/.X11-unix/X${DISPLAY#:}" ]] && break
  sleep 0.25
done

x11vnc -display "$DISPLAY" -nopw -no6 -listen 127.0.0.1 -xkb -ncache 10 -ncache_cr -forever &
VNC_PID=$!
websockify --web /usr/share/novnc 127.0.0.1:6080 localhost:5900 &
WEBSOCKIFY_PID=$!

CHROME_BIN="${CHROME_BIN:-}"
if [[ -z "$CHROME_BIN" ]]; then
  for candidate in /ms-playwright/chromium-*/chrome-linux/chrome /ms-playwright/chromium-*/chrome-linux64/chrome; do
    if [[ -x "$candidate" ]]; then
      CHROME_BIN="$candidate"
      break
    fi
  done
fi

if [[ -z "$CHROME_BIN" || ! -x "$CHROME_BIN" ]]; then
  echo "Could not find the Chromium executable under /ms-playwright. Set CHROME_BIN to its path." >&2
  exit 1
fi

"$CHROME_BIN" \
  --no-sandbox \
  --disable-setuid-sandbox \
  --disable-dev-shm-usage \
  --no-first-run \
  --no-default-browser-check \
  --remote-debugging-port=9222 \
  --remote-debugging-address="${CDP_BIND_ADDRESS:-127.0.0.1}" \
  --remote-allow-origins='*' \
  --user-data-dir=/app/storage/browser-worker-profile \
  --window-size=1920,1080 &
CHROME_PID=$!

shutdown() {
  kill "$CHROME_PID" "$WEBSOCKIFY_PID" "$VNC_PID" "$XVFB_PID" 2>/dev/null || true
}
trap shutdown EXIT TERM INT

wait "$CHROME_PID"
