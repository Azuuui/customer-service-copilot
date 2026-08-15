"""BGE-M3 向量生成器；依赖与模型均延迟加载。"""

from datetime import datetime, timezone

from app.config import EMBEDDING_DIMENSION, EMBEDDING_LOCAL_FILES_ONLY, EMBEDDING_MODEL, EMBEDDING_MODEL_REVISION


class EmbeddingUnavailableError(RuntimeError):
    """Embedding 运行环境或模型不可用。"""


class BgeM3Embedder:
    def __init__(self, model_name=EMBEDDING_MODEL, model_version=EMBEDDING_MODEL_REVISION):
        self.model_name = model_name
        self.model_version = model_version
        self.dimension = EMBEDDING_DIMENSION
        self._model = None

    def _load(self):
        if self._model is not None:
            return self._model
        try:
            from sentence_transformers import SentenceTransformer
            self._model = SentenceTransformer(
                self.model_name,
                revision=self.model_version,
                local_files_only=EMBEDDING_LOCAL_FILES_ONLY,
            )
        except Exception as exc:
            raise EmbeddingUnavailableError(f"无法加载 Embedding 模型 {self.model_name}: {exc}") from exc
        actual_dimension = self._model.get_sentence_embedding_dimension()
        if actual_dimension != self.dimension:
            raise EmbeddingUnavailableError(
                f"Embedding 维度不匹配：期望 {self.dimension}，实际 {actual_dimension}"
            )
        return self._model

    def embed(self, text):
        return self.embed_many([text])[0]

    def embed_many(self, texts, batch_size=32):
        model = self._load()
        vectors = model.encode(
            list(texts),
            batch_size=batch_size,
            normalize_embeddings=True,
            show_progress_bar=len(texts) > batch_size,
        )
        return [vector.tolist() for vector in vectors]

    def metadata(self):
        return {
            "model": self.model_name,
            "version": self.model_version,
            "dimension": self.dimension,
            "generated_at": datetime.now(timezone.utc),
        }
