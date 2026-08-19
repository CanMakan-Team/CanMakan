# Runtime image for the already-verified Spring Boot JAR (Java 21).
# CI copies the staged JAR in as app.jar; this file does not compile the backend.
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN groupadd --system canmakan && useradd --system --gid canmakan --no-create-home canmakan

COPY --chown=canmakan:canmakan app.jar /app/app.jar

USER canmakan

EXPOSE 8080

ENV SERVER_PORT=8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
