package com.saurabh.stockdecision.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "app.api-key=test-key")
@AutoConfigureMockMvc
class DecisionControllerTest {

    private static final String URL = "/api/v1/decisions";

    @Autowired
    private MockMvc mvc;

    @Test
    void returnsDecisionWithValidKey() throws Exception {
        mvc.perform(post(URL).header("X-API-KEY", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stockName":"aapl","averagePurchasePrice":150,"currentPrice":185}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockName").value("AAPL"))
                .andExpect(jsonPath("$.decision").value("SELL"))
                .andExpect(jsonPath("$.changePercent").value(23.33))
                .andExpect(jsonPath("$.poweredBy").value("Basic calculation"))
                .andExpect(jsonPath("$.jevEvaluation").isEmpty())
                .andExpect(jsonPath("$.jevError").isEmpty());
    }

    @Test
    void rejectsMissingKey() throws Exception {
        mvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stockName":"AAPL","averagePurchasePrice":150,"currentPrice":150}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsWrongKey() throws Exception {
        mvc.perform(post(URL).header("X-API-KEY", "nope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void returnsValidationErrors() throws Exception {
        mvc.perform(post(URL).header("X-API-KEY", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stockName":"","averagePurchasePrice":-1}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.stockName").exists())
                .andExpect(jsonPath("$.errors.averagePurchasePrice").exists())
                .andExpect(jsonPath("$.errors.currentPrice").exists());
    }

    @Test
    void returnsBadRequestForMalformedJson() throws Exception {
        mvc.perform(post(URL).header("X-API-KEY", "test-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void healthAndSwaggerAreOpen() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
    }
}
