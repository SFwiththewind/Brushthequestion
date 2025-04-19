FROM openjdk:8-jdk-alpine
WORKDIR /app
COPY target/BQ-question-1.0-SNAPSHOT.jar app.jar
EXPOSE 8004
ENTRYPOINT ["java", "-jar", "app.jar"]