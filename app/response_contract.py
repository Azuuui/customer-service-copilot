"""HTTP 返回契约：只展示 Excel 原始答案，不暴露自动承接推断。"""


def _answer_blocks(result):
    scenarios = result.get("scenarios") or []
    if scenarios and all(scenario.get("raw_text") is not None for scenario in scenarios):
        return [
            {
                "text": scenario["raw_text"],
                "ticket_recommendations": list(scenario.get("ticket_recommendations", [])),
                "tags": list(scenario.get("tags", [])),
                "notes": list(scenario.get("notes", [])),
            }
            for scenario in scenarios
            if scenario["raw_text"].strip()
        ]
    return [
        {"text": line, "ticket_recommendations": [], "tags": [], "notes": []}
        for line in result.get("original_answer", "").splitlines()
        if line.strip()
    ]


def _sanitize_result(result):
    sanitized = {key: value for key, value in result.items() if key not in {"scenarios", "serviceability", "answer_status", "match_type", "rrf_score"}}
    sanitized["answer_blocks"] = _answer_blocks(result)
    return sanitized


def build_search_response(query, results, limit=5, embedding_status="ready", offset=0, degradation_reason=None):
    page = list(results)[offset:offset + limit]
    return {
        "query": query,
        "limit": limit,
        "offset": offset,
        "embedding_status": embedding_status,
        "degraded": embedding_status != "ready",
        "degradation_reason": degradation_reason,
        "results": [_sanitize_result(result) for result in page],
    }
