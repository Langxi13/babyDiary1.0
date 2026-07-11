FROM maven:3.9.11-eclipse-temurin-17 AS build

WORKDIR /workspace/backend
COPY backend/pom.xml ./pom.xml
COPY backend/src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B -Dmaven.test.skip=true package

FROM eclipse-temurin:17-jre-jammy

RUN groupadd --system baby-diary \
    && useradd --system --gid baby-diary --home-dir /app baby-diary \
    && install -d -o baby-diary -g baby-diary /app/config /data/images /data/objects

WORKDIR /app
COPY --from=build --chown=baby-diary:baby-diary /workspace/backend/target/Baby-Diary-0.0.1-SNAPSHOT.jar /app/app.jar
COPY --chown=baby-diary:baby-diary config/application-prod.yml /app/config/application-prod.yml

USER baby-diary
EXPOSE 10002

HEALTHCHECK --interval=10s --timeout=5s --start-period=45s --retries=12 \
  CMD curl --fail --silent http://127.0.0.1:10002/actuator/health >/dev/null || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
