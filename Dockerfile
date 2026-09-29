FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
RUN mvn --batch-mode dependency:go-offline
COPY src ./src
RUN mvn --batch-mode package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/target/jobflow-*.jar app.jar
RUN addgroup -S jobflow && adduser -S jobflow -G jobflow
USER jobflow
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=75","-jar","/app/app.jar"]
