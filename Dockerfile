# ---- build ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B package -DskipTests

# ---- run ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /app/target/stock-decision-engine-*.jar app.jar
USER app
EXPOSE 8080
# JEV_API_KEY must be supplied at runtime: docker run -e JEV_API_KEY=...
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
