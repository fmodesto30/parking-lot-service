# ---- build stage ----
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B package -DskipTests -Dspotless.check.skip=true

# ---- runtime stage ----
FROM eclipse-temurin:21-jre
RUN groupadd --system garage && useradd --system --gid garage garage
USER garage:garage
WORKDIR /app

COPY --from=build /workspace/target/parking-lot-service-*.jar app.jar

EXPOSE 3003
# Liveness only: readiness (config synced) is a separate probe by design —
# the container must not be killed just because the simulator is not up yet.
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
  CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/3003 && printf 'GET /actuator/health/liveness HTTP/1.1\\r\\nHost: localhost\\r\\nConnection: close\\r\\n\\r\\n' >&3 && grep -q '\"status\":\"UP\"' <&3"]

ENTRYPOINT ["java", "-jar", "app.jar"]
