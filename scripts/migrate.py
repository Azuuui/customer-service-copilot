#!/usr/bin/env python3
"""按文件名顺序执行数据库迁移。"""

import os
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]


def main():
    try:
        import psycopg
    except ImportError as error:
        raise SystemExit("缺少 psycopg，请先安装 requirements.txt") from error
    database_url = os.environ.get("DATABASE_URL")
    if not database_url:
        raise SystemExit("缺少 DATABASE_URL")
    migrations = sorted((ROOT / "db/migrations").glob("*.sql"))
    with psycopg.connect(database_url) as connection:
        with connection.cursor() as cursor:
            cursor.execute("CREATE TABLE IF NOT EXISTS schema_migrations (version text PRIMARY KEY, applied_at timestamptz NOT NULL DEFAULT now())")
            cursor.execute("SELECT version FROM schema_migrations")
            applied = {row[0] for row in cursor.fetchall()}
            for migration in migrations:
                if migration.name in applied:
                    continue
                cursor.execute(migration.read_text(encoding="utf-8"))
                cursor.execute("INSERT INTO schema_migrations(version) VALUES (%s)", (migration.name,))
                print(f"applied {migration.name}")


if __name__ == "__main__":
    main()
