# syntax=docker/dockerfile:1

# Build: compila o JAR com Maven e JDK 21
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cache das dependências do Maven
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
# Os testes já rodaram no pipeline
RUN mvn -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/alerta-predio.jar extract --layers --launcher --destination target/extracted

# Runtime: só a JRE, sem Maven nem código-fonte
FROM eclipse-temurin:21-jre-alpine

# O pipeline passa o SHA do commit, exibido em /api/info
ARG APP_VERSION=dev
ENV APP_VERSION=${APP_VERSION} \
    PORT=8080 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"

# Roda como usuário sem privilégios
RUN addgroup -S alerta && adduser -S -G alerta alerta \
 && mkdir -p /app/logs && chown -R alerta:alerta /app
WORKDIR /app

# Camadas do Spring Boot, da que menos muda para a que mais muda
COPY --from=build --chown=alerta:alerta /build/target/extracted/dependencies/ ./
COPY --from=build --chown=alerta:alerta /build/target/extracted/spring-boot-loader/ ./
COPY --from=build --chown=alerta:alerta /build/target/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=alerta:alerta /build/target/extracted/application/ ./

USER alerta
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- "http://localhost:${PORT}/actuator/health" | grep -q '"status":"UP"' || exit 1

# exec deixa o Java como PID 1 para receber o SIGTERM
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
