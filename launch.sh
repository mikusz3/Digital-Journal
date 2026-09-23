#!/usr/bin/env bash
set -euo pipefail
BUJO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
exec "$BUJO_ROOT/node_modules/electron/dist/electron" "$BUJO_ROOT" "$@"
