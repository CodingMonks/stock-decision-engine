package com.saurabh.stockdecision.api;

import com.saurabh.stockdecision.model.DecisionRequest;
import com.saurabh.stockdecision.model.DecisionResponse;
import com.saurabh.stockdecision.service.DecisionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/decisions")
public class DecisionController {

    private final DecisionService decisionService;

    public DecisionController(DecisionService decisionService) {
        this.decisionService = decisionService;
    }

    @Operation(summary = "Get a BUY / SELL / HOLD decision for a stock position")
    @PostMapping
    public DecisionResponse decide(@Valid @RequestBody DecisionRequest request) {
        return decisionService.decide(request);
    }
}
