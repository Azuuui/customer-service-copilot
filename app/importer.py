"""将清洗 JSON 与原 Excel 元数据合并为数据库导入记录。"""

import hashlib
import json
from collections import Counter
from datetime import datetime

from app.text import normalize_query


def _parse_datetime(value):
    text = "" if value is None else str(value).strip()
    if not text:
        return None
    for pattern in ("%Y-%m-%d %H:%M:%S", "%Y-%m-%d"):
        try:
            return datetime.strptime(text, pattern)
        except ValueError:
            continue
    return None


def compose_search_text(record):
    parts = [record.get("standard_question", "")]
    parts.extend(record.get("user_questions", []))
    parts.extend(record.get("keywords", []))
    parts.append(record.get("category", ""))
    # 原始答案仅参与召回，不会被改写；可覆盖“制冰机”等只出现在处置答案中的业务词。
    parts.append(record.get("original_reply", ""))
    return "\n".join(str(part).strip() for part in parts if str(part).strip())


def _content_hash(record):
    canonical = {key: value for key, value in record.items() if key not in {"embedding", "content_hash"}}
    payload = json.dumps(canonical, ensure_ascii=False, sort_keys=True, default=str, separators=(",", ":"))
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()


def prepare_import_records(json_records, source_rows, tokenizer):
    if len(json_records) != len(source_rows):
        raise ValueError("JSON 与原 Excel 行数不一致，禁止按错误顺序导入")
    prepared = []
    for json_record, source_row in zip(json_records, source_rows):
        standard_question = json_record.get("standard_question", "").strip()
        source_question = str(source_row.get("标准问题(必填)", "")).strip()
        if standard_question != source_question:
            raise ValueError(f"标准问题不一致：{json_record.get('id')} / {source_question}")
        search_text = compose_search_text(json_record)
        tokens = list(tokenizer(search_text))
        frequencies = dict(Counter(tokens))
        phrases = [{"type": "standard", "value": standard_question, "position": 0}]
        phrases.extend({"type": "user_question", "value": value, "position": index} for index, value in enumerate(json_record.get("user_questions", [])))
        phrases.extend({"type": "keyword", "value": value, "position": index} for index, value in enumerate(json_record.get("keywords", [])))
        answer_lines = [line.strip() for line in json_record.get("original_reply", "").splitlines() if line.strip()]
        phrases.extend({"type": "keyword", "value": value, "position": 1000 + index} for index, value in enumerate(answer_lines))
        item = {
            "source_key": json_record["id"],
            "standard_question": standard_question,
            "standard_question_normalized": normalize_query(standard_question),
            "category": json_record.get("category", ""),
            "user_questions": list(json_record.get("user_questions", [])),
            "keywords": list(json_record.get("keywords", [])),
            "scenarios": list(json_record.get("scenarios", [])),
            "original_answer": json_record.get("original_reply", ""),
            "search_text": search_text,
            "search_tokens": tokens,
            "term_frequencies": frequencies,
            "document_length": len(tokens),
            "phrases": phrases,
            "is_active": str(source_row.get("生效状态(TRUE/FALSE)", "")).strip().upper() == "TRUE",
            "valid_from": _parse_datetime(source_row.get("生效时间(yyyy-mm-dd)")),
            "valid_to": _parse_datetime(source_row.get("失效时间(yyyy-mm-dd)")),
            "source_created_by": str(source_row.get("创建人", "")).strip() or None,
            "source_updated_by": str(source_row.get("修改人", "")).strip() or None,
            "source_created_at": _parse_datetime(source_row.get("创建时间")),
            "source_updated_at": _parse_datetime(source_row.get("修改时间")),
        }
        item["content_hash"] = _content_hash(item)
        prepared.append(item)
    return prepared
