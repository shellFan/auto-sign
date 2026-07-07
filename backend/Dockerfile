FROM maven:3.8-openjdk-8 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

FROM openjdk:8-jre-slim
WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
RUN mkdir -p /app/storage/profiles /app/storage/cookies /app/storage/screenshots /app/storage/logs
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
