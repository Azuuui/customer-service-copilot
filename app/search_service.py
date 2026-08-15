"""精确匹配、BM25、向量召回和 RRF 融合。"""

from app.bm25 import BM25Index
from app.embedding import EmbeddingUnavailableError
from app.response_contract import build_search_response
from app.rrf import reciprocal_rank_fusion
from app.text import normalize_query


class HybridSearchService:
    def __init__(self, repository, embedder=None):
        self.repository = repository
        self.embedder = embedder

    def search(self, query, limit=5, offset=0):
        normalized = normalize_query(query)
        if not normalized:
            raise ValueError("query_required")

        exact_matches = self.repository.exact_matches(normalized)
        documents = self.repository.active_documents()
        bm25_ranked = [(item_id, score) for item_id, score in BM25Index(documents).rank(query) if score > 0][:50]
        vector_ranked = []
        embedding_status = "unavailable"
        degradation_reason = "embedding_not_configured"
        if self.embedder is not None:
            try:
                vector = self.embedder.embed(query)
                vector_ranked = self.repository.vector_search(vector, 50)
                embedding_status = "ready"
                degradation_reason = None
            except EmbeddingUnavailableError as exc:
                # 语义召回不可用时明确降级到精确 + BM25，不制造假向量。
                vector_ranked = []
                degradation_reason = str(exc)

        exact_ids = [item_id for item_id, _ in exact_matches]
        # 短词已有明确命中时，抑制仅靠低相似度进入的语义噪声；无明确命中时保留高置信语义召回。
        semantic_floor = 0.45 if not exact_ids else 0.60
        vector_ranked = [item for item in vector_ranked
                         if item[1] >= semantic_floor or item[0] in exact_ids]
        vector_ids = [item_id for item_id, _ in vector_ranked]
        vector_set_for_filter = set(vector_ids)
        bm25_ids = [item_id for item_id, _ in bm25_ranked
                    if not exact_ids or item_id in exact_ids or item_id in vector_set_for_filter]
        fused = reciprocal_rank_fusion([bm25_ids, vector_ids])
        ordered_ids = exact_ids + [item_id for item_id, _ in fused if item_id not in exact_ids]
        documents_by_id = {document["id"]: document for document in self.repository.get_documents(ordered_ids[:50])}
        exact_kind = {item_id: kind for item_id, kind in exact_matches}
        bm25_set = set(bm25_ids)
        vector_set = set(vector_ids)
        rrf_scores = dict(fused)

        results = []
        for item_id in ordered_ids:
            document = documents_by_id.get(item_id)
            if not document:
                continue
            methods = []
            if item_id in exact_kind:
                methods.append("direct" if exact_kind[item_id] in {"standard", "user_question"} else "keyword")
            if item_id in bm25_set and "keyword" not in methods:
                methods.append("bm25")
            if item_id in vector_set:
                methods.append("semantic")
            if "direct" in methods:
                match_type = "直接命中"
            elif "keyword" in methods or "bm25" in methods:
                match_type = "混合命中" if "semantic" in methods else "关键词匹配"
            else:
                match_type = "语义相似"
            results.append({
                "id": document["id"],
                "standard_question": document["standard_question"],
                "user_questions": document.get("user_questions", []),
                "keywords": document.get("keywords", []),
                "category": document["category"],
                "original_answer": document.get("original_answer", ""),
                "scenarios": document.get("scenarios", []),
                "valid_from": document.get("valid_from"),
                "valid_to": document.get("valid_to"),
                "match_methods": methods,
                "match_type": match_type,
                "rrf_score": rrf_scores.get(item_id, 0.0),
            })
        return build_search_response(query, results, limit, embedding_status, offset, degradation_reason)
