FROM eclipse-temurin:25-jre
WORKDIR /app
COPY target/client2-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
