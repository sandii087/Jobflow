FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/jobflow-*.jar app.jar
RUN addgroup -S jobflow && adduser -S jobflow -G jobflow
USER jobflow
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]
