package com.saurabh.stockdecision.model;

import java.util.Map;

/**
 * Jev's structured judgment of a decision.
 *
 * @param model               Jev model that produced the judgment
 * @param soundness           how sound the rules-engine decision is, on a 0..3 rubric
 * @param reasonConsistency   truth value in [0, 1] that the stated reason matches the numbers
 * @param jevDecision         Jev's own BUY / SELL / HOLD pick
 * @param agreesWithRules     whether Jev's pick equals the rules-engine decision
 */
public record JevEvaluation(
        String model,
        Soundness soundness,
        double reasonConsistency,
        JevDecision jevDecision,
        boolean agreesWithRules
) {

    /**
     * @param score       continuous rubric position, 0 (Unsound) to {@code maxLevel} (Sound)
     * @param maxLevel    highest rubric level
     * @param label       label of the nearest rubric level
     * @param confidence  Jev's confidence in the placement, in [0, 1]
     */
    public record Soundness(double score, int maxLevel, String label, double confidence) {
    }

    /**
     * @param decision       Jev's pick
     * @param probabilities  probability per option
     * @param confidence     Jev's confidence in the pick, in [0, 1]
     */
    public record JevDecision(Decision decision, Map<String, Double> probabilities, double confidence) {
    }
}
