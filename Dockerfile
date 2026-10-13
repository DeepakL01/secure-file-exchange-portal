# ==============================================================================
# Multi-Stage Dockerfile for Secure File Exchange Portal
# Base: Eclipse Temurin OpenJDK 21
# ==============================================================================

# Stage 1: Build the application
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Copy Maven Wrapper and configuration
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -B

# Copy source code and build executable JAR
COPY src/ src/
RUN ./mvnw clean package -DskipTests -B

# Stage 2: Lightweight runtime environment
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Create unprivileged application user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Create persistent storage directory with proper permissions
RUN mkdir -p /app/storage/uploads && chown -R appuser:appgroup /app

# Copy built JAR from builder stage
COPY --from=builder --chown=appuser:appgroup /build/target/secure-file-exchange-*.jar /app/app.jar

USER appuser

ENV SERVER_PORT=8080 \
    STORAGE_DIR=/app/storage/uploads

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
