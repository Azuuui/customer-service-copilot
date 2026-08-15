"""基于正式中文分词结果的 BM25 实现。"""

import math
from collections import Counter

from app.text import tokenize_search_text


class BM25Index:
    def __init__(self, documents, k1=1.5, b=0.75):
        self.documents = list(documents)
        self.k1 = k1
        self.b = b
        self.count = len(self.documents)
        self.avgdl = sum(doc.get("document_length", 0) for doc in self.documents) / self.count if self.count else 0
        self.document_frequency = Counter()
        for document in self.documents:
            self.document_frequency.update(set(document.get("tokens", [])))

    def _idf(self, token):
        df = self.document_frequency.get(token, 0)
        return math.log(1 + (self.count - df + 0.5) / (df + 0.5))

    def rank(self, query):
        query_tokens = set(tokenize_search_text(query))
        ranked = []
        for document in self.documents:
            frequencies = document.get("term_frequencies", {})
            length = document.get("document_length", 0)
            score = 0.0
            for token in query_tokens:
                frequency = frequencies.get(token, 0)
                if not frequency:
                    continue
                denominator = frequency + self.k1 * (1 - self.b + self.b * length / self.avgdl) if self.avgdl else 1
                score += self._idf(token) * frequency * (self.k1 + 1) / denominator
            ranked.append((document["id"], score))
        return sorted(ranked, key=lambda item: (-item[1], item[0]))
