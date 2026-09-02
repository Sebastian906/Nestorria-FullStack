# Stage 1: Build
FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

# Copy Maven wrapper and pom.xml first (dependency caching)
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .

# Download dependencies (Docker layer caching)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src/ src/

# Compile without tests (tests run in CI)
RUN ./mvnw package -DskipTests -B

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S spring && adduser -S spring -G spring

# Copy JAR from builder
COPY --from=builder /app/target/*.jar app.jar

# Change ownership
RUN chown -R spring:spring /app

USER spring

# Port (Render uses PORT env var, default 4000 matches application.properties)
EXPOSE 4000

# Health check using wget (available via busybox in Alpine)
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD wget -qO- http://localhost:${PORT:-4000}/actuator/health || exit 1

# Run
ENTRYPOINT ["java", "-jar", "app.jar"]