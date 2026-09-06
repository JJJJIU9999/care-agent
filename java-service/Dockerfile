FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY docker-maven-settings.xml ./
COPY src ./src
RUN mvn --settings docker-maven-settings.xml --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /workspace/target/care-agent-service-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
