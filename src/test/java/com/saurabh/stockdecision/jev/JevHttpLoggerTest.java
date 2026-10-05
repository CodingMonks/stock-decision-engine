package com.saurabh.stockdecision.jev;

import com.saurabh.stockdecision.security.SecretRedactor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(OutputCaptureExtension.class)
class JevHttpLoggerTest {

    private static final String URL = "https://api.typesafe.ai/v1/systemone";
    private static final String TYPESAFE_KEY = "ts-secret-key-123";

    private final RestClient.Builder builder = RestClient.builder()
            .requestInterceptor(new JevHttpLogger(new SecretRedactor(List.of(TYPESAFE_KEY))));
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final RestClient client = builder.build();

    @Test
    void bodyIsStillReadableAfterLogging() {
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"model\":\"jev-latest\"}", MediaType.APPLICATION_JSON));

        String body = client.post().uri(URL).body("{}").retrieve().body(String.class);

        assertThat(body).isEqualTo("{\"model\":\"jev-latest\"}");
        server.verify();
    }

    @Test
    void errorBodyIsStillReadableAfterLogging() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON).body("{\"error\":\"bad key\"}"));

        assertThatThrownBy(() -> client.post().uri(URL).body("{}").retrieve().body(String.class))
                .isInstanceOfSatisfying(HttpClientErrorException.class,
                        e -> assertThat(e.getResponseBodyAsString()).isEqualTo("{\"error\":\"bad key\"}"));
    }

    @Test
    void neverLogsTheApiKey(CapturedOutput output) {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"error\":\"invalid key " + TYPESAFE_KEY + "\"}"));

        assertThatThrownBy(() -> client.post().uri(URL)
                .header("Authorization", "Bearer " + TYPESAFE_KEY)
                .body("{}").retrieve().body(String.class));

        assertThat(output).contains("Jev <-- 401").doesNotContain(TYPESAFE_KEY);
    }
}
