FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*
COPY mvnw pom.xml ./
COPY .mvn/ .mvn/
RUN --mount=type=cache,target=/root/.m2 \
    chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/target/integration-catalog-*.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=10s --start-period=60s --retries=3 \
    CMD curl --fail --silent --show-error --max-time 8 http://127.0.0.1:8080/actuator/health/readiness > /dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
