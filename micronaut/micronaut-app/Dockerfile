FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Pre-built Shadow JAR — run ./gradlew :micronaut-app:shadowJar (or compare.sh) first
COPY build/libs/micronaut-app-0.1.0-all.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
