# --- Build stage ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# --- Runtime stage ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S utemp && adduser -S utemp -G utemp \
    && mkdir -p /app/storage && chown utemp:utemp /app/storage
COPY --from=build /app/target/*.jar app.jar
USER utemp
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
