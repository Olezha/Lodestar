# Stage 1: Extract Spring Boot layered jar
FROM eclipse-temurin:21-jre-alpine AS extractor
WORKDIR /workspace
ARG JAR_FILE=build/libs/lodestar-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --launcher --destination extracted

# Stage 2: Minimalist Alpine JRE 21 Runtime (<150 MB)
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Run as non-root user for cloud-native security
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Copy extracted layers in order of change frequency (best layer caching)
COPY --from=extractor --chown=spring:spring /workspace/extracted/dependencies/ ./
COPY --from=extractor --chown=spring:spring /workspace/extracted/spring-boot-loader/ ./
COPY --from=extractor --chown=spring:spring /workspace/extracted/snapshot-dependencies/ ./
COPY --from=extractor --chown=spring:spring /workspace/extracted/application/ ./

# JVM Container awareness & cgroups v2 dynamic sizing
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

# Use exec to ensure JVM process receives SIGTERM directly as PID 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
