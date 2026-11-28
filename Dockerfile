# 1) Build the React app
FROM node:22-alpine AS web
WORKDIR /web
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 2) Build the Spring Boot jar, with the React build served as static files
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn -q -B dependency:go-offline
COPY src ./src
COPY --from=web /web/dist ./src/main/resources/static
RUN mvn -q -B -DskipTests package

# 3) Small runtime image
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --create-home --uid 10001 appuser
COPY --from=build --chown=appuser:appuser /app/target/stokvel-tracker.jar app.jar
# Set COOKIE_SECURE=false only when serving over plain HTTP (e.g. docker run on localhost).
ENV JAVA_TOOL_OPTIONS="-Xmx300m" COOKIE_SECURE=true
USER appuser
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
