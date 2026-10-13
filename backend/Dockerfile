FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S -G app app
WORKDIR /app

COPY --from=build /workspace/target/backend-*.jar app.jar

ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
USER app:app

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=6 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
