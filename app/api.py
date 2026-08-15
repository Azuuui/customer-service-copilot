"""仅供 Spring Boot 调用的内部检索 HTTP 接口。"""

from pathlib import Path

from app.config import DATABASE_URL
from app.embedding import BgeM3Embedder, EmbeddingUnavailableError
from app.json_repository import JsonKnowledgeRepository
from app.repository import PostgresKnowledgeRepository
from app.search_service import HybridSearchService
from app.index_service import KnowledgeIndexService

try:
    from fastapi import FastAPI, HTTPException
    from pydantic import BaseModel, Field
except ImportError:  # 允许仅运行纯算法测试
    FastAPI = None
    BaseModel = object
    Field = lambda default=None, **_: default


BASE_DIR = Path(__file__).resolve().parent.parent


class SearchRequest(BaseModel):
    query: str = Field(min_length=1, max_length=500)
    limit: int = Field(default=4, ge=1, le=4)
    offset: int = Field(default=0, ge=0, le=1000)


if FastAPI is not None:
    app = FastAPI(title="企业客服内部检索服务", version="2.0.0")
    _repository = PostgresKnowledgeRepository(DATABASE_URL)
    _fallback_repository = JsonKnowledgeRepository(BASE_DIR / "data" / "demo-knowledge.json")
    _embedder = BgeM3Embedder()
    _index_service = KnowledgeIndexService(DATABASE_URL, _embedder)

    @app.get("/internal/v1/health")
    def health():
        database_status = "ready"
        database_reason = None
        try:
            stats = _repository.health()
        except Exception as exc:
            database_status = "unavailable"
            database_reason = str(exc)
            stats = {"active_count": 0, "embedded_count": 0, "total_count": 0}
        embedding_reason = None
        try:
            _embedder._load()
            embedding_status = "ready"
        except EmbeddingUnavailableError as exc:
            embedding_status = "unavailable"
            embedding_reason = str(exc)
        status = "ready" if database_status == embedding_status == "ready" and stats["embedded_count"] > 0 else "degraded"
        return {
            "status": status,
            "database": {"status": database_status, "reason": database_reason},
            "embedding": {"status": embedding_status, "reason": embedding_reason, "dimension": _embedder.dimension},
            **stats,
        }

    @app.post("/internal/v1/search")
    def search(request: SearchRequest):
        if not request.query.strip():
            raise HTTPException(status_code=422, detail="query_required")
        try:
            return HybridSearchService(_repository, _embedder).search(request.query, request.limit, request.offset)
        except ValueError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc
        except Exception as exc:
            response = HybridSearchService(_fallback_repository).search(request.query, request.limit, request.offset)
            response["degraded"] = True
            response["degradation_reason"] = f"database_unavailable: {exc}"
            return response

    @app.post("/internal/v1/index/{source_key}")
    def rebuild_index(source_key: str):
        try:
            return _index_service.reindex(source_key)
        except KeyError as exc:
            raise HTTPException(status_code=404, detail=str(exc)) from exc

    # 兼容旧的内部契约；浏览器入口仍只允许 Spring Boot。
    app.add_api_route("/api/health", health, methods=["GET"], include_in_schema=False)
    app.add_api_route("/api/search", search, methods=["POST"], include_in_schema=False)
else:
    app = None
