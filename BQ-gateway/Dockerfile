FROM openjdk:8-jdk-alpine
WORKDIR /app
COPY target/BQ-gateway-1.0-SNAPSHOT.jar app.jar
EXPOSE 8002
ENTRYPOINT ["java", "-jar", "app.jar"]