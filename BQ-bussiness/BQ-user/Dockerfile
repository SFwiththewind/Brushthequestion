FROM openjdk:8-jdk-alpine
WORKDIR /app
COPY target/BQ-user-1.0-SNAPSHOT.jar app.jar
EXPOSE 8001
ENTRYPOINT ["java", "-jar", "app.jar"]