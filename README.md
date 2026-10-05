# Stock Decision Engine

Spring Boot 4 / Java 21 service that returns a **BUY / SELL / HOLD** decision from a stock's
average purchase price and current price. The rules engine is **fully offline**: no market-data or
database calls. Everything it needs comes in the request. If `TYPESAFE_API_KEY` is set, each decision
is also scored by **Jev** (TypeSafe AI). See below.

**Architecture, diagrams, technology stack and secrets handling:** [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)

## Decision rules (configurable in `application.yml`)

`changePercent = (currentPrice - averagePurchasePrice) / averagePurchasePrice × 100`

| Condition | Decision | Why |
|---|---|---|
| change ≥ +20% (`take-profit-percent`) | SELL | Take profit |
| change ≤ −15% (`stop-loss-percent`) | SELL | Stop loss |
| −15% < change ≤ −5% (`average-down-percent`) | BUY | Average down |
| otherwise | HOLD | Within normal range |

Override without rebuilding, e.g. `DECISION_TAKE_PROFIT_PERCENT=25`.
The app refuses to start if `average-down-percent` is not smaller than `stop-loss-percent`.

## Security: API key

`/api/**` requires header `X-API-KEY` matching the **`API_KEY`** environment variable.
The key is never stored in code or config, and the app fails fast at startup if it is missing.
Health (`/actuator/health`) and Swagger UI stay open.

`TYPESAFE_API_KEY` is also read only from the environment. Neither key is ever logged or returned:
headers aren't logged, and `SecretRedactor` masks both keys in every Jev log line and `jevError`
message. See [Secrets handling](docs/ARCHITECTURE.md#secrets-handling).

## Jev evaluation (optional)

`JevClient` (`jev/`) uses the [TypeSafe Java SDK](https://spring.io/blog/2026/09/21/spring-ai-typesafe-structured-judgment)
to ask Jev three typed questions about each decision in one `systemOne` call:

| Field | Jev primitive | Meaning |
|---|---|---|
| `soundness.score` | `Score` (0 Unsound → 1 Questionable → 2 Reasonable → 3 Sound) | Continuous rubric position, with `label`, `confidence` |
| `reasonConsistency` | `Noul` | Truth value in [0, 1] that the reason matches the numbers |
| `jevDecision` | `Choice` (BUY / SELL / HOLD) | Jev's own pick, with `probabilities`, `confidence` |
| `agreesWithRules` | derived | Jev's pick equals the rules decision |

Jev sees only the position, the thresholds and the rules decision. It has no market data.
Every response has a `poweredBy` field:

- `"AI (Jev by TypeSafe AI)"` means Jev evaluated the decision, and `jevEvaluation` holds its judgment.
- `"Basic calculation"` means `TYPESAFE_API_KEY` is unset or the Jev call failed. The decision comes
  from the rules alone and `jevEvaluation` is `null`.

When Jev fails, the response also carries `jevError`:

```json
"poweredBy": "Basic calculation",
"jevEvaluation": null,
"jevError": { "code": "RATE_LIMITED", "message": "Too many requests", "httpStatus": 429, "requestId": "req-9" }
```

`code` is one of `AUTHENTICATION`, `PERMISSION_DENIED`, `BAD_REQUEST`, `NOT_FOUND`, `RATE_LIMITED`,
`SERVER_ERROR`, `TIMEOUT`, `CONNECTION`, `INVALID_RESPONSE`, `UNKNOWN`. `requestId` is Jev's
`x-typesafe-request-id`, which you can quote to TypeSafe support.

### Jev logs

| Level | Logged |
|---|---|
| INFO | Startup status (enabled + URL/model/timeout, or a warning that it's disabled); each call's stock, action, model and URL; every HTTP attempt (`Jev -->` / `Jev <--`) with status, latency and request id; the result (scores, Jev's pick, tokens, total latency) |
| WARN | Transient failures (timeout, connection, rate limit, 5xx) and Jev error bodies |
| ERROR | Configuration problems (401/403/400/404) and unknown errors |
| DEBUG | Full request state, raw Jev response bodies and failure stack traces |

Set `JEV_LOG_LEVEL=DEBUG` to see bodies. Headers are never logged, so the API key never appears.
SDK retries show up as repeated `Jev -->` lines.

| Env var | Default |
|---|---|
| `TYPESAFE_API_KEY` | unset → Jev disabled |
| `TYPESAFE_BASE_URL` | `https://api.typesafe.ai` |
| `TYPESAFE_DEFAULT_MODEL` | `jev-latest` |
| `JEV_LOG_LEVEL` | `INFO` |

## Run

```bash
export API_KEY=change-me
export TYPESAFE_API_KEY=...   # optional
mvn spring-boot:run
```

```bash
curl -s -X POST http://localhost:8080/api/v1/decisions \
  -H "Content-Type: application/json" -H "X-API-KEY: change-me" \
  -d '{"stockName":"AAPL","averagePurchasePrice":150,"currentPrice":185}'
```

```json
{
  "stockName": "AAPL",
  "averagePurchasePrice": 150,
  "currentPrice": 185,
  "changePercent": 23.33,
  "decision": "SELL",
  "reason": "Gain of 23.33% reached the take-profit target of +20%.",
  "evaluatedAt": "2026-10-04T12:00:00Z",
  "poweredBy": "AI (Jev by TypeSafe AI)",
  "jevEvaluation": {
    "model": "jev-latest",
    "soundness": { "score": 2.6, "maxLevel": 3, "label": "Sound: ...", "confidence": 0.78 },
    "reasonConsistency": 0.97,
    "jevDecision": { "decision": "SELL", "probabilities": { "SELL": 0.81, "HOLD": 0.17, "BUY": 0.02 }, "confidence": 0.81 },
    "agreesWithRules": true
  }
}
```

Swagger UI: http://localhost:8080/swagger-ui.html (click **Authorize** and paste the key).

## Test

```bash
mvn test
```

## Docker

```bash
docker build -t stock-decision-engine .
docker run -p 8080:8080 -e API_KEY=change-me -e TYPESAFE_API_KEY=... stock-decision-engine
```

## Structure

```
api/        DecisionController, GlobalExceptionHandler (RFC 7807 errors)
config/     DecisionProperties (thresholds), TypeSafeProperties, AppConfig (Clock, OpenAPI)
jev/        JevClient (Score / Noul / Choice judgment, error mapping), JevConfig (only when TYPESAFE_API_KEY
            is set), JevHttpLogger (HTTP logs), JevStartupLogger, JevResult
model/      DecisionRequest, DecisionResponse, Decision, JevEvaluation, JevError
security/   ApiKeyFilter (API_KEY), SecretRedactor (masks keys in logs and errors)
service/    DecisionService (pure rules engine)
```

> This tool applies simple mechanical rules and is not financial advice.
