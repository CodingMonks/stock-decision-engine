package com.saurabh.stockdecision.api;

import com.saurabh.stockdecision.jev.JevClient;
import com.saurabh.stockdecision.jev.JevResult;
import com.saurabh.stockdecision.model.Decision;
import com.saurabh.stockdecision.model.JevError;
import com.saurabh.stockdecision.model.JevEvaluation;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.api-key=test-key")
@AutoConfigureMockMvc
class DecisionControllerJevTest {

    private static final String BODY = """
            {"stockName":"AAPL","averagePurchasePrice":150,"currentPrice":185}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JevClient jevClient;

    @Test
    void includesJevEvaluation() throws Exception {
        when(jevClient.evaluate(any())).thenReturn(new JevResult.Success(new JevEvaluation("jev-latest",
                new JevEvaluation.Soundness(2.6, 3, "Sound", 0.78), 0.97,
                new JevEvaluation.JevDecision(Decision.HOLD, Map.of("HOLD", 0.6, "SELL", 0.4), 0.6),
                false)));

        mvc.perform(post("/api/v1/decisions").header("X-API-KEY", "test-key")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("SELL"))
                .andExpect(jsonPath("$.poweredBy").value("AI (Jev by TypeSafe AI)"))
                .andExpect(jsonPath("$.jevEvaluation.soundness.score").value(2.6))
                .andExpect(jsonPath("$.jevEvaluation.soundness.label").value("Sound"))
                .andExpect(jsonPath("$.jevEvaluation.jevDecision.decision").value("HOLD"))
                .andExpect(jsonPath("$.jevEvaluation.agreesWithRules").value(false))
                .andExpect(jsonPath("$.jevError").isEmpty());
    }

    @Test
    void reportsJevErrorAndFallsBackToBasicCalculation() throws Exception {
        when(jevClient.evaluate(any())).thenReturn(new JevResult.Failure(
                new JevError(JevError.Code.RATE_LIMITED, "Too many requests", 429, "req-9")));

        mvc.perform(post("/api/v1/decisions").header("X-API-KEY", "test-key")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").value("SELL"))
                .andExpect(jsonPath("$.poweredBy").value("Basic calculation"))
                .andExpect(jsonPath("$.jevEvaluation").isEmpty())
                .andExpect(jsonPath("$.jevError.code").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.jevError.httpStatus").value(429))
                .andExpect(jsonPath("$.jevError.requestId").value("req-9"));
    }
}
