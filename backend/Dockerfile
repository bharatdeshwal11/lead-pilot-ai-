FROM eclipse-temurin:21-jdk-alpine AS build
RUN --mount=type=bind,target=/source \
    mkdir -p /tmp/leadpilot && cp -R /source/. /tmp/leadpilot/ && \
    cd /tmp/leadpilot && ./mvnw -B -ntp -Dmaven.test.skip=true package && \
    cp target/leadpilot-api-1.0.0.jar /app.jar && rm -rf /tmp/leadpilot /root/.m2

FROM eclipse-temurin:21-jre-alpine
COPY --chmod=444 --from=build /app.jar /leadpilot-api.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/leadpilot-api.jar"]
