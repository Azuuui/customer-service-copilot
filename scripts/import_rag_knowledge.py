#!/usr/bin/env python3
"""将虚构演示 JSON 导入 PostgreSQL。"""

import argparse
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.config import DATABASE_URL
from app.db_import import embeddings_are_current, import_records
from app.embedding import BgeM3Embedder
from app.importer import prepare_import_records
from app.text import configure_dictionary, tokenize_search_text


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--json", type=Path, required=True)
    parser.add_argument("--database-url", default=DATABASE_URL)
    parser.add_argument("--skip-embeddings", action="store_true")
    parser.add_argument("--batch-size", type=int, default=32)
    args = parser.parse_args()
    records = json.loads(args.json.read_text(encoding="utf-8"))
    source_rows = [
        {"标准问题(必填)": record["standard_question"], "生效状态(TRUE/FALSE)": "TRUE"}
        for record in records
    ]
    dictionary = []
    for record in records:
        dictionary.extend([record.get("standard_question", ""), *record.get("user_questions", []), *record.get("keywords", [])])
    configure_dictionary(dictionary)
    prepared = prepare_import_records(records, source_rows, tokenize_search_text)
    metadata = None
    if not args.skip_embeddings:
        if embeddings_are_current(prepared, args.database_url):
            print(f"知识与真实向量已就绪，跳过重复生成：{len(prepared)} 条")
            return
        embedder = BgeM3Embedder()
        vectors = embedder.embed_many([record["search_text"] for record in prepared], batch_size=args.batch_size)
        for record, vector in zip(prepared, vectors):
            record["embedding"] = vector
        metadata = embedder.metadata()
    count = import_records(prepared, args.database_url, metadata)
    print(f"导入完成：{count} 条；语义向量：{'已生成' if metadata else '跳过（未写入假向量）'}")


if __name__ == "__main__":
    main()
