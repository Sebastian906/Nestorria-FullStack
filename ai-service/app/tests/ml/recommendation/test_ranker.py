"""Tests for HybridRanker."""

from app.ml.recommendation.ranker import HybridRanker, ScoreComponents

class TestHybridRanker:
    def test_rank_basic(self):
        ranker = HybridRanker(graph_weight=0.4, content_weight=0.3, collab_weight=0.3)
        ranked = ranker.rank(
            candidate_ids=["p1", "p2", "p3"],
            graph_scores={"p1": 0.9, "p2": 0.5, "p3": 0.1},
            content_scores={"p1": 0.8, "p2": 0.6, "p3": 0.3},
            collab_scores={"p1": 0.7, "p2": 0.4, "p3": 0.2},
        )

        assert len(ranked) == 3
        assert ranked[0].property_id == "p1"  # highest
        assert ranked[-1].property_id == "p3"  # lowest
        assert ranked[0].score >= ranked[1].score >= ranked[2].score

    def test_rank_without_content(self):
        ranker = HybridRanker(graph_weight=0.4, content_weight=0.3, collab_weight=0.3)
        ranked = ranker.rank(
            candidate_ids=["p1", "p2"],
            graph_scores={"p1": 0.9, "p2": 0.5},
            content_scores=None,
            collab_scores={"p1": 0.7, "p2": 0.4},
        )

        assert len(ranked) == 2
        # Weights should be redistributed
        assert ranked[0].property_id == "p1"

    def test_rank_graph_only(self):
        ranker = HybridRanker()
        ranked = ranker.rank(
            candidate_ids=["p1", "p2"],
            graph_scores={"p1": 0.9, "p2": 0.5},
        )

        assert len(ranked) == 2
        assert ranked[0].property_id == "p1"
        # All weight on graph
        assert ranked[0].breakdown.graph == 0.9

    def test_score_breakdown(self):
        ranker = HybridRanker(graph_weight=0.4, content_weight=0.3, collab_weight=0.3)
        ranked = ranker.rank(
            candidate_ids=["p1"],
            graph_scores={"p1": 0.8},
            content_scores={"p1": 0.6},
            collab_scores={"p1": 0.4},
        )

        r = ranked[0]
        assert r.breakdown.graph == 0.8
        assert r.breakdown.content == 0.6
        assert r.breakdown.collab == 0.4
        expected = 0.4 * 0.8 + 0.3 * 0.6 + 0.3 * 0.4
        assert abs(r.score - expected) < 0.001

    def test_assign_variant_deterministic(self):
        v1 = HybridRanker.assign_variant("user-123")
        v2 = HybridRanker.assign_variant("user-123")
        assert v1 == v2  # same user → same variant

    def test_assign_variant_distribution(self):
        variants = [HybridRanker.assign_variant(f"user-{i}") for i in range(1000)]
        treatment_count = sum(1 for v in variants if v == "treatment")
        # Should be roughly 50%
        assert 400 < treatment_count < 600

    def test_weight_normalization(self):
        ranker = HybridRanker(graph_weight=2.0, content_weight=1.0, collab_weight=1.0)
        # Weights should be normalized to sum to 1.0
        assert abs(ranker.graph_weight + ranker.content_weight + ranker.collab_weight - 1.0) < 0.01