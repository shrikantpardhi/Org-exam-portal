# ---------------------------------------------------------------------------
# Backend container image (multi-stage).
# Build context: ./backend
# ---------------------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Resolve dependencies first so they are cached when only sources change.
COPY pom.xml ./
RUN mvn -B -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------------------------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime

WORKDIR /app

# Run as an unprivileged user.
RUN groupadd --system --gid 1001 exam \
    && useradd --system --uid 1001 --gid exam --home-dir /app exam

COPY --from=build /workspace/target/exam-platform-backend-*.jar /app/app.jar

# Create the writable data root as the runtime user *before* it is declared a volume.
#
# Docker seeds a named volume from the image path it mounts over. If the path does not exist in the
# image it is created by the daemon owned by root, and the unprivileged "exam" user then cannot write
# there — which silently broke every generated report artifact (ReportArtifactStore.write threw
# "Report artifact could not be stored") and would equally break uploads. Creating the directory here,
# owned by exam, means the volume inherits that ownership on first use.
RUN mkdir -p /app/data/uploads /app/data/reports \
    && chown -R exam:exam /app/data
RUN chown -R exam:exam /app

USER exam
EXPOSE 8080

ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
