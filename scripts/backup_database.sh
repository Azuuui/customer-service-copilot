#!/usr/bin/env bash
set -euo pipefail
backup_dir="${1:-backups}"
mkdir -p "$backup_dir"
stamp="$(date +%Y%m%d-%H%M%S)"
target="$backup_dir/copilot-$stamp.dump"
docker compose exec -T db pg_dump -U "${POSTGRES_USER:-demo_user}" -d "${POSTGRES_DB:-demo}" --format=custom --no-owner > "$target"
printf '%s\n' "$target"
