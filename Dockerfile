# ===== Stage 1: Build =====
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Cache Maven wrapper + dependencies before source changes
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Copy source and build
COPY src src
RUN ./mvnw clean package -DskipTests -B

# ===== Stage 2: Runtime =====
FROM eclipse-temurin:21-jre-alpine

# Security: run as non-root
RUN addgroup -S bookly && adduser -S bookly -G bookly

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

RUN chown bookly:bookly app.jar
USER bookly

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
