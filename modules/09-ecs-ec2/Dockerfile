FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
COPY target/ecs-ec2-task-tracker.jar app.jar

EXPOSE 8089
HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
  CMD wget -q -O- http://localhost:8089/healthz || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
