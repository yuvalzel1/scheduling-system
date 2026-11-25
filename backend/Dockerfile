# syntax=docker/dockerfile:1

# ---- Stage 1: build the jar and split it into layers -------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Dependencies first: this layer is reused until pom.xml changes.
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline

COPY src/ src/
# Tests need Docker themselves (Testcontainers), so they run on the developer machine or in CI,
# not inside this image build.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -DskipTests package \
    && cp target/*.jar application.jar \
    && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---- Stage 2: small runtime image ---------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /application

RUN addgroup -S spring && adduser -S -G spring -u 1001 spring

# One layer per kind of content, least-changing first, so a code change only replaces the last one.
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER spring
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "application.jar"]
