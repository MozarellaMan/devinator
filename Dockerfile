# --- build stage -----------------------------------------------------------
FROM gradle:9.6.1-jdk25 AS build
WORKDIR /app

COPY build.gradle settings.gradle gradlew ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true

COPY src ./src
RUN ./gradlew jar --no-daemon

# --- runtime stage -----------------------------------------------------------
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/build/libs/devinbridge-1.0.0.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
