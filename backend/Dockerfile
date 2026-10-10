# Backend image (spec 10.2): JDK build stage -> JRE runtime, non-root, healthcheck.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -ntp -q dependency:go-offline
COPY src src
# Commit id for /actuator/info (spec 9.6); passed by `make up` / CI. There is no .git here, so
# git-commit-id-maven-plugin is skipped: it would write an empty git.properties into
# target/classes, and the resources step does not overwrite that newer file (D-042).
ARG GIT_COMMIT=""
RUN if [ -n "$GIT_COMMIT" ]; then \
      printf 'git.commit.id=%s\ngit.commit.id.abbrev=%s\n' "$GIT_COMMIT" "$(echo "$GIT_COMMIT" | cut -c1-7)" \
        > src/main/resources/git.properties; \
    fi \
 && ./mvnw -B -ntp -q -DskipTests -Dspotless.check.skip=true -Dmaven.gitcommitid.skip=true package \
 && cp target/feature-flag-service-*.jar /src/app.jar

FROM eclipse-temurin:21-jre
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --no-create-home app
WORKDIR /app
COPY --from=build --chown=app:app /src/app.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
HEALTHCHECK --interval=5s --timeout=3s --start-period=10s --retries=3 \
  CMD curl -fs "http://localhost:${SERVER_PORT:-8080}/actuator/health/readiness" >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
