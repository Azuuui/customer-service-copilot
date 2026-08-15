#!/usr/bin/env python3
"""通过唯一 8080 入口执行生产前健康和黄金查询验收。"""
import json
import os
import urllib.request
from pathlib import Path

BASE = os.getenv("COPILOT_BASE_URL", "http://127.0.0.1:8080").rstrip("/")
ROOT = Path(__file__).resolve().parents[1]


def request(path, body=None):
    payload = None if body is None else json.dumps(body, ensure_ascii=False).encode()
    req = urllib.request.Request(BASE + path, data=payload, headers={"Content-Type": "application/json", "X-Anonymous-Session": "release-verification"})
    with urllib.request.urlopen(req, timeout=60) as response:
        return json.load(response)


health = request("/api/v1/health")
retrieval = health["retrieval"]
assert health["status"] == "ok" and health["persistence"] == "jdbc"
assert retrieval["status"] == "ready" and retrieval["embedding"]["dimension"] == 1024
assert retrieval["embedded_count"] == retrieval["total_count"] and retrieval["embedded_count"] > 0

checks = json.loads((ROOT / "data/golden-query-set.json").read_text(encoding="utf-8"))
for check in checks:
    result = request("/api/v1/query", {"query": check["query"], "limit": 4, "offset": 0, "requestKind": "query"})
    questions = [item["standard_question"] for item in result["results"]]
    assert result["results"], f"{check['query']}: 空结果"
    assert any(question in questions for question in check["expected_any"]), f"{check['query']}: {questions}"
    assert all(item.get("original_answer") for item in result["results"]), f"{check['query']}: 原始答案缺失"
    assert any("semantic" in item.get("match_methods", []) for item in result["results"]), f"{check['query']}: 语义链路未命中"
    print(f"PASS {check['query']}: {questions[0]}")
print(f"HEALTH ready vectors={retrieval['embedded_count']}")
