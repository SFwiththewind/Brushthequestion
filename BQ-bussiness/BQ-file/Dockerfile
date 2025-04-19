FROM openjdk:8-jdk-alpine
WORKDIR /app
COPY target/BQ-file-1.0-SNAPSHOT.jar app.jar
EXPOSE 8003
ENTRYPOINT ["java", "-jar", "app.jar"]