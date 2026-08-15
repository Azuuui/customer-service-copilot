"""运行配置。"""

import os


DATABASE_URL = os.getenv("DATABASE_URL", "postgresql://rag:rag@127.0.0.1:54329/rag")
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "BAAI/bge-m3")
EMBEDDING_MODEL_REVISION = os.getenv("EMBEDDING_MODEL_REVISION", "main")
EMBEDDING_DIMENSION = 1024
EMBEDDING_LOCAL_FILES_ONLY = os.getenv("EMBEDDING_LOCAL_FILES_ONLY", "true").lower() == "true"
