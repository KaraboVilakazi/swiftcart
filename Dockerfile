# ─────────────────────────────────────────────────────────────
# Stage 1: Build
# Use a full JDK image to compile and package the application.
# ─────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom first — layer cache means dependencies are only
# re-downloaded when pom.xml changes.
COPY pom.xml ./
RUN mvn dependency:resolve -q

# Copy source and build
COPY src ./src
RUN mvn package -DskipTests

# ─────────────────────────────────────────────────────────────
# Stage 2: Runtime
# Use a slim JRE — significantly smaller than the JDK image.
# ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-jammy AS runtime

WORKDIR /app

# Non-root user for security
RUN groupadd -r swiftcart && useradd -r -g swiftcart swiftcart
USER swiftcart

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

# Use exec form so the JVM receives signals directly (clean shutdown)
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]
