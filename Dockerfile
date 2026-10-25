# ==============================================================================
# Multi-Stage Dockerfile for Secure File Exchange Portal
# Base: Eclipse Temurin OpenJDK 21
# ==============================================================================

# Stage 1: Build the application

# Stage 1: Build
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN sed -i 's/\r$//' mvnw && chmod +x mvnw
RUN ./mvnw dependency:go-offline -B

COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Run
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
RUN mkdir -p /app/storage/uploads \
    && chown -R appuser:appgroup /app

COPY --from=builder --chown=appuser:appgroup \
    /build/target/secure-file-exchange-*.jar /app/app.jar

USER appuser

ENV SERVER_PORT=8080
ENV STORAGE_DIR=/app/storage/uploads

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
