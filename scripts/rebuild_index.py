#!/usr/bin/env python3
"""重建正式检索索引。"""

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from app.config import DATABASE_URL
from app.repository import _connect


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--database-url", default=DATABASE_URL)
    args = parser.parse_args()
    with _connect(args.database_url) as connection:
        connection.autocommit = True
        with connection.cursor() as cursor:
            cursor.execute("REINDEX INDEX idx_knowledge_search_vector")
            cursor.execute("REINDEX INDEX idx_knowledge_embedding_hnsw")
    print("GIN 与 HNSW 索引已重建")


if __name__ == "__main__":
    main()
