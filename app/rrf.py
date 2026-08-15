"""Reciprocal Rank Fusion。"""


def reciprocal_rank_fusion(ranked_lists, k=60):
    scores = {}
    for ranked in ranked_lists:
        for rank, item_id in enumerate(ranked, start=1):
            scores[item_id] = scores.get(item_id, 0.0) + 1 / (k + rank)
    return sorted(scores.items(), key=lambda item: (-item[1], item[0]))
