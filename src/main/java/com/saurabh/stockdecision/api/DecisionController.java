package com.saurabh.stockdecision.api;

import com.saurabh.stockdecision.jev.JevClient;
import com.saurabh.stockdecision.jev.JevResult;
import com.saurabh.stockdecision.model.DecisionRequest;
import com.saurabh.stockdecision.model.DecisionResponse;
import com.saurabh.stockdecision.service.DecisionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/decisions")
public class DecisionController {

    private final DecisionService decisionService;
    private final ObjectProvider<JevClient> jevClient;

    public DecisionController(DecisionService decisionService, ObjectProvider<JevClient> jevClient) {
        this.decisionService = decisionService;
        this.jevClient = jevClient;
    }

    @Operation(summary = "Get a BUY / SELL / HOLD decision for a stock position, scored by Jev when configured")
    @PostMapping
    public DecisionResponse decide(@Valid @RequestBody DecisionRequest request) {
        DecisionResponse decision = decisionService.decide(request);
        JevClient jev = jevClient.getIfAvailable();
        if (jev == null) {
            return decision;
        }
        return switch (jev.evaluate(decision)) {
            case JevResult.Success success -> decision.withJevEvaluation(success.evaluation());
            case JevResult.Failure failure -> decision.withJevError(failure.error());
        };
    }
}
