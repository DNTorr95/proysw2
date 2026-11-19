# ---- Compilación ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# ---- Ejecución ----
FROM eclipse-temurin:21-jre-alpine
# Mismo UID/GID (1001) que rpa-worker: ambos escriben en el volumen documental compartido
RUN addgroup -S -g 1001 app && adduser -S -u 1001 -G app app \
    && mkdir -p /var/storage/documentos && chown -R app:app /var/storage/documentos
WORKDIR /app
COPY --from=build /app/target/gestion-administrativa.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
    CMD wget -qO- http://localhost:8080/api/actuator/health | grep -q UP || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
