FROM eclipse-temurin:25-jre-alpine

WORKDIR /app

# Pre-built JAR — run ./gradlew :spring-app:bootJar (or compare.sh) first
COPY build/libs/spring-app-0.1.0.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
