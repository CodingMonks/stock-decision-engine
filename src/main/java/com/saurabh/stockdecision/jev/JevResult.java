package com.saurabh.stockdecision.jev;

import com.saurabh.stockdecision.model.JevError;
import com.saurabh.stockdecision.model.JevEvaluation;

/**
 * Outcome of a Jev call: either an evaluation or the reason it failed.
 */
public sealed interface JevResult {

    record Success(JevEvaluation evaluation) implements JevResult {
    }

    record Failure(JevError error) implements JevResult {
    }
}
