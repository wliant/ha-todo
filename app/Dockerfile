# syntax=docker/dockerfile:1

# ---- build ----
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

# Wrapper and build scripts first so dependency resolution caches independently
# of source changes.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies --quiet || true

COPY src ./src
# Unit and integration tests run in CI and locally, not in the image build;
# integrationTest needs a Docker daemon which is not available here.
RUN ./gradlew --no-daemon bootJar -x test

# ---- runtime ----
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S app && adduser -S -G app app \
    && apk add --no-cache curl

COPY --from=build /build/build/libs/*.jar /app/app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
    CMD curl -fsS http://127.0.0.1:8080/actuator/health | grep -q '"status":"UP"'

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
