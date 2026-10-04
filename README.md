# Stock Decision Engine

Spring Boot 3.5 / Java 21 service that returns a **BUY / SELL / HOLD** decision from a stock's
average purchase price and current price. It is **fully offline**: no market-data, database or
third-party calls. Everything it needs comes in the request.

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

## Security: JEV API key

`/api/**` requires header `X-API-KEY` matching the **`JEV_API_KEY`** environment variable.
The key is never stored in code or config, and the app fails fast at startup if it is missing.
Health (`/actuator/health`) and Swagger UI stay open.

## Run

```bash
export JEV_API_KEY=change-me
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
  "evaluatedAt": "2026-10-04T12:00:00Z"
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
docker run -p 8080:8080 -e JEV_API_KEY=change-me stock-decision-engine
```

## Structure

```
api/        DecisionController, GlobalExceptionHandler (RFC 7807 errors)
config/     DecisionProperties (thresholds), AppConfig (Clock, OpenAPI)
model/      DecisionRequest, DecisionResponse, Decision
security/   ApiKeyFilter (JEV_API_KEY)
service/    DecisionService (pure rules engine)
```

> This tool applies simple mechanical rules and is not financial advice.
