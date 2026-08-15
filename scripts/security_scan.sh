#!/usr/bin/env bash
set -euo pipefail

tracked_env_files="$(git ls-files | rg '(^|/)\.env($|\.)' | rg -v '(^|/)\.env\.example$' || true)"
tracked_sensitive_files="$(git ls-files | rg -i '(^|/)(\.DS_Store|.*\.(pem|key|p12|pfx|jks|dump|bak|zip|tar|tar\.gz))$|(^|/)(node_modules|target|dist|coverage|\.idea|\.vscode)(/|$)' || true)"

if [[ -n "${tracked_env_files}" || -n "${tracked_sensitive_files}" ]]; then
  printf '发现禁止提交的文件\n%s\n%s\n' "${tracked_env_files}" "${tracked_sensitive_files}" >&2
  exit 1
fi

private_key_pattern='BEGIN .*PRIVATE KEY'
internal_pattern='小钉|xiaoding|啄木鸟|xiujiadian|git-ai\.|harbor\.|/Users/[^/[:space:]]+|wxid_'
personal_pattern='1[3-9][0-9]{9}|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}'
hardcoded_pattern='(CLIENT_SECRET|PASSWORD|API_KEY|ACCESS_TOKEN)=[^$<{[:space:]]+'

for pattern in "${private_key_pattern}" "${internal_pattern}" "${personal_pattern}" "${hardcoded_pattern}"; do
  if git grep -n -I -E -- "${pattern}" -- ':!scripts/security_scan.sh'; then
    printf '发现疑似敏感信息\n' >&2
    exit 1
  fi
done

commit_emails="$(git log --format='%ae%n%ce' | sort -u)"
if printf '%s\n' "${commit_emails}" | rg -v '^[A-Za-z0-9._%+-]+@(gmail\.com|users\.noreply\.github\.com)$' | rg -q .; then
  printf '提交元数据邮箱必须是 Gmail 或 GitHub 隐私邮箱\n' >&2
  exit 1
fi

printf '安全扫描通过\n'
