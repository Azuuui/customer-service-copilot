FROM node:22-alpine AS frontend-build
WORKDIR /workspace/frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN --mount=type=cache,target=/root/.npm npm ci --no-audit --no-fund
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.10-eclipse-temurin-21 AS backend-build
WORKDIR /workspace
COPY backend/pom.xml backend/pom.xml
COPY backend/src backend/src
COPY --from=frontend-build /workspace/frontend/dist backend/src/main/resources/static
RUN --mount=type=cache,target=/root/.m2 mvn -f backend/pom.xml package -Dmaven.test.skip=true -q

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY --from=backend-build /workspace/backend/target/customer-service-copilot-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
