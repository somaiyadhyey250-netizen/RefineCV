# ==============================================================================
# RefineCV — Production Dockerfile for Render Deployment
# Multi-stage build: Java 21 JDK (build) -> Java 21 JRE + Tesseract OCR (runtime)
# ==============================================================================

# Stage 1: Build stage
FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /build

# Copy Maven wrapper and configuration
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .

# Ensure wrapper script is executable
RUN chmod +x ./mvnw

# Copy source code and tessdata
COPY src src
COPY tessdata tessdata

# Package application JAR (tests verified separately in CI / pre-deploy regression)
RUN ./mvnw clean package -DskipTests

# Stage 2: Production Runtime stage
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Install native Tesseract OCR and English language dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    tesseract-ocr \
    libtesseract-dev \
    tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/*

# Run as non-root user for security
RUN groupadd -r refinecv && useradd -r -g refinecv -m -d /app refinecv

# Copy built Spring Boot fat JAR from builder
COPY --from=builder /build/target/resume-analyzer-0.0.1-SNAPSHOT.jar app.jar

# Copy verified tessdata directory containing eng.traineddata
COPY --from=builder /build/tessdata ./tessdata

# Set ownership to non-root user
RUN chown -R refinecv:refinecv /app
USER refinecv

# Default server port (Render injects dynamic PORT environment variable)
ENV PORT=8080
EXPOSE 8080

# Launch Spring Boot application
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
