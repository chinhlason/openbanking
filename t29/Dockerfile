FROM eclipse-temurin:25-jre
WORKDIR /app
COPY target/t29-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
