#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAVA_BIN="${JAVA_HOME:+${JAVA_HOME}/bin/}java"
PWCLI="${PWCLI:-${CODEX_HOME:-${HOME}/.codex}/skills/playwright/scripts/playwright_cli.sh}"
APP_JAR="${ROOT_DIR}/backend/target/customer-service-copilot-0.1.0-SNAPSHOT.jar"
SESSION_NAME="copilot-phase1"
VERIFY_PORT="${VERIFY_PORT:-18080}"
BASE_URL="http://127.0.0.1:${VERIFY_PORT}"

if [[ ! -f "${APP_JAR}" || ! -f "${ROOT_DIR}/frontend/dist/index.html" ]]; then
  echo "请先构建 backend 和 frontend" >&2
  exit 1
fi

COPILOT_PERSISTENCE=memory \
COPILOT_AUTH_MODE=mock \
COPILOT_BOOTSTRAP_ADMIN_ID=admin-001 \
COPILOT_FRONTEND_DIST="${ROOT_DIR}/frontend/dist/" \
SERVER_PORT="${VERIFY_PORT}" \
"${JAVA_BIN}" -jar "${APP_JAR}" >/tmp/copilot-phase1-backend.log 2>&1 &
BACKEND_PID=$!

cleanup() {
  kill "${BACKEND_PID}" 2>/dev/null || true
  bash "${PWCLI}" --session "${SESSION_NAME}" close >/dev/null 2>&1 || true
}
trap cleanup EXIT

run_playwright() {
  local output
  output="$(bash "${PWCLI}" --session "${SESSION_NAME}" "$@" 2>&1)"
  echo "${output}"
  if grep -q "### Error" <<<"${output}"; then
    return 1
  fi
}

for _ in $(seq 1 30); do
  if curl --fail --silent "${BASE_URL}/api/v1/health" >/dev/null; then
    break
  fi
  sleep 1
done
curl --fail --silent "${BASE_URL}/api/v1/health" >/dev/null

run_playwright open "${BASE_URL}/"
run_playwright run-code "async (page) => {
  const input = page.getByRole('searchbox', {name: '输入问题或关键词'});
  if (await input.count() !== 1) throw new Error('query_input_missing');
  await input.fill('马桶疏通');
  await page.getByRole('button', {name: '查询'}).click();
  await page.getByText('暂未找到相关答案，可直接上报补充。').waitFor();
  await page.goto('${BASE_URL}/admin');
  if (await page.getByRole('navigation', {name: '管理模块'}).count() !== 0) throw new Error('admin_visible_without_login');
  await page.getByRole('button', {name: '模拟登录'}).click();
  const nav = page.getByRole('navigation', {name: '管理模块'});
  await nav.waitFor();
  if (await nav.getByRole('link').count() !== 10) throw new Error('admin_module_count_invalid');
  await page.setViewportSize({width: 390, height: 844});
  await page.goto('${BASE_URL}/');
  const main = page.getByRole('main', {name: '知识查询'});
  if (!(await main.isVisible())) throw new Error('mobile_main_hidden');
}"
echo "阶段一浏览器交互验证通过"
