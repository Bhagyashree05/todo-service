# ─── Stage 1: Build ────────────────────────────────────────────────────────────
# Uses a full JDK + Maven image to compile and package the application.
# Dependencies are downloaded in a separate layer so they are cached by Docker
# and not re-downloaded on every build unless pom.xml changes.
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy POM first and resolve dependencies — this layer is cached until pom.xml changes
COPY pom.xml .
RUN apk add --no-cache maven && mvn dependency:go-offline -q

# Copy source and build (tests skipped here — run them separately via `mvn test`)
COPY src ./src
RUN mvn package -DskipTests -q

# ─── Stage 2: Runtime ──────────────────────────────────────────────────────────
# Uses a lean JRE-only image (~200MB vs ~600MB for a full JDK).
# No build tools, no source code, no Maven cache in the final image.
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create a non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy only the built JAR from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Log directory (app writes JSON logs here)
RUN mkdir -p /app/logs

EXPOSE 8080

# Use exec form to ensure signals (SIGTERM) are handled by the JVM, not a shell
ENTRYPOINT ["java", "-jar", "app.jar"]
