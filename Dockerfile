# --- build stage -----------------------------------------------------------
FROM gradle:8.10-jdk21 AS build
WORKDIR /app

# Cache dependency resolution separately from source changes.
COPY build.gradle settings.gradle ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon || true

COPY src ./src
RUN gradle jar --no-daemon

# --- runtime stage -----------------------------------------------------------
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/build/libs/devinbridge-1.0.0.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
