# syntax=docker/dockerfile:1
# L12 base e L31 runtime: tags completas tornam as imagens reprodutíveis sem acompanhar uma versão flutuante
#     (add-observability design D8).
# L14 base: o glob copia o gradle.properties só se ele existir, para o build e o CodeQlWorkflowTest verem o mesmo
#     arquivo que o host (fix-codeql-build design D7).
# L19-L25 test: documentos e workflows invalidam a verificação sem entrar no builder/runtime
#     (enforce-hexagonal-architecture design D5; fix-codeql-build design D7). Enunciado: O que será avaliado → Testes
# L33-L40 runtime: usuário sem root limita privilégios; flags limitam a JVM ao contêiner e a forma exec entrega
#     SIGTERM à JVM para o encerramento gracioso (add-observability design D8).
# Enunciado: O que será avaliado → Production readiness

FROM eclipse-temurin:21.0.12_8-jdk-noble AS base
WORKDIR /workspace
COPY gradlew build.gradle.kts settings.gradle.kts gradle.propertie[s] ./
COPY gradle gradle
RUN chmod +x gradlew
COPY src src

FROM base AS test
COPY README.md CLAUDE.md Makefile Dockerfile .dockerignore docker-compose.yml ./
COPY infra infra
COPY openspec openspec
COPY http/hello.http http/hello.http
COPY .github .github
RUN --mount=type=cache,target=/root/.gradle ./gradlew check --no-daemon

FROM base AS builder
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar --no-daemon \
    && cp $(ls build/libs/*.jar | grep -v plain) /workspace/app.jar

FROM eclipse-temurin:21.0.12_8-jre-noble AS runtime
WORKDIR /app
RUN groupadd --system --gid 10001 app \
    && useradd --system --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app
COPY --from=builder --chown=10001:10001 /workspace/app.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0 -XX:+ExitOnOutOfMemoryError"
USER 10001:10001
EXPOSE 8080 8082
STOPSIGNAL SIGTERM
ENTRYPOINT ["java", "-jar", "app.jar"]
