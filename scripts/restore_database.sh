#!/usr/bin/env bash
set -euo pipefail
if [[ $# -ne 1 || ! -f "$1" ]]; then printf '用法: %s <backup.dump>\n' "$0" >&2; exit 2; fi
docker compose exec -T db pg_restore -U "${POSTGRES_USER:-demo_user}" -d "${POSTGRES_DB:-demo}" --clean --if-exists --no-owner < "$1"
