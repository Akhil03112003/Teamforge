# Build only. No credentials or local .env files enter any stage.
FROM node:24-bookworm-slim AS frontend
WORKDIR /src/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run typecheck && npm run build

FROM maven:3.9.12-eclipse-temurin-21 AS backend
WORKDIR /src
COPY backend/pom.xml backend/pom.xml
RUN mvn --batch-mode --no-transfer-progress -f backend/pom.xml dependency:go-offline
COPY backend/src backend/src
COPY --from=frontend /src/frontend/dist frontend/dist
# CI runs the full PostgreSQL suite; image construction does not provision a database.
RUN mvn --batch-mode --no-transfer-progress -f backend/pom.xml -Pweb -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app
RUN groupadd --gid 10001 teamforge && useradd --uid 10001 --gid teamforge --no-create-home teamforge
COPY --from=backend --chown=10001:10001 /src/backend/target/teamforge-0.0.1-SNAPSHOT.jar /app/teamforge.jar
USER 10001:10001
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
STOPSIGNAL SIGTERM
ENTRYPOINT ["java","-jar","/app/teamforge.jar"]
