#!/usr/bin/env bash
set -euo pipefail
# 快速停用唯一业务入口；数据库和数据卷保持运行/可恢复。
docker compose stop backend
