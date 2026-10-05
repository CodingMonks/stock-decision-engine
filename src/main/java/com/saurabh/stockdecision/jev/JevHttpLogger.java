package com.saurabh.stockdecision.jev;

import com.saurabh.stockdecision.security.SecretRedactor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springaicommunity.typesafe.exception.TypeSafeApiException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Logs every HTTP exchange with Jev: method, URL, status, latency and Jev request id at INFO;
 * request and response bodies at DEBUG. Headers are never logged, so the API key cannot leak;
 * everything else that is logged passes through {@link SecretRedactor} as a second line of defence.
 * Each SDK retry passes through here and is logged separately.
 */
class JevHttpLogger implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(JevHttpLogger.class);
    private static final int MAX_BODY_CHARS = 4_000;

    private final SecretRedactor redactor;

    JevHttpLogger(SecretRedactor redactor) {
        this.redactor = redactor;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        String uri = redactor.redact(request.getURI().toString());
        log.info("Jev --> {} {} ({} bytes)", request.getMethod(), uri, body.length);
        log.debug("Jev --> body: {}", truncate(body));

        long start = System.nanoTime();
        try {
            ClientHttpResponse response = execution.execute(request, body);
            byte[] responseBody = response.getBody().readAllBytes();
            log.info("Jev <-- {} {} {} in {} ms, requestId={}",
                    response.getStatusCode().value(), request.getMethod(), uri, elapsedMs(start),
                    response.getHeaders().getFirst(TypeSafeApiException.REQUEST_ID_HEADER));
            if (response.getStatusCode().isError()) {
                log.warn("Jev <-- error body: {}", truncate(responseBody));
            } else {
                log.debug("Jev <-- body: {}", truncate(responseBody));
            }
            return new BufferedResponse(response, responseBody);
        } catch (IOException e) {
            log.warn("Jev <-- no response for {} {} after {} ms: {}",
                    request.getMethod(), uri, elapsedMs(start), redactor.redact(e.toString()));
            throw e;
        }
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private String truncate(byte[] body) {
        String text = redactor.redact(new String(body, StandardCharsets.UTF_8));
        return text.length() <= MAX_BODY_CHARS ? text : text.substring(0, MAX_BODY_CHARS) + "...(truncated)";
    }

    /** Lets the SDK read the body after it was consumed for logging. */
    private record BufferedResponse(ClientHttpResponse delegate, byte[] body) implements ClientHttpResponse {

        @Override
        public HttpStatusCode getStatusCode() throws IOException {
            return delegate.getStatusCode();
        }

        @Override
        public String getStatusText() throws IOException {
            return delegate.getStatusText();
        }

        @Override
        public HttpHeaders getHeaders() {
            return delegate.getHeaders();
        }

        @Override
        public InputStream getBody() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
