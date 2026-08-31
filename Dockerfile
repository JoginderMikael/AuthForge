FROM maven:3.9.12-eclipse-temurin-25-alpine AS build

WORKDIR /workspace
COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn .mvn
RUN mvn -B dependency:go-offline

COPY src src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:25-jre-alpine

RUN apk add --no-cache curl \
    && addgroup -S authforge \
    && adduser -S authforge -G authforge

WORKDIR /app
COPY --from=build /workspace/target/authforge-*.jar /app/authforge.jar

USER authforge
EXPOSE 8082

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/authforge.jar"]
