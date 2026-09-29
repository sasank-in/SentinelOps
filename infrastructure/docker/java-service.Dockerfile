# syntax=docker/dockerfile:1.7
# Builds any Spring Boot service in the Maven reactor. Build context is the repo root:
#   docker build -f infrastructure/docker/java-service.Dockerfile --build-arg SERVICE=order-service .

FROM maven:3.9-eclipse-temurin-21 AS build
ARG SERVICE
WORKDIR /workspace

# POMs first so dependency resolution is cached until a POM changes.
COPY pom.xml .
COPY libs/chaos-spring-boot-starter/pom.xml libs/chaos-spring-boot-starter/
COPY services/api-gateway/pom.xml services/api-gateway/
COPY services/order-service/pom.xml services/order-service/
COPY services/payment-service/pom.xml services/payment-service/
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl services/${SERVICE} -am dependency:go-offline -DexcludeReactor=true

COPY libs libs
COPY services services
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl services/${SERVICE} -am package -DskipTests \
 && cp services/${SERVICE}/target/${SERVICE}-*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 app
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER app

# Container-aware heap sizing; exit on OOM so the orchestrator restarts the service
# (the MEMORY failure scenario relies on this).
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
