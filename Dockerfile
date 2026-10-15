# Stage 1: Extract Spring Boot layered jar
FROM eclipse-temurin:21-jre-alpine AS extractor
WORKDIR /workspace
ARG JAR_FILE=build/libs/lodestar-0.0.1-SNAPSHOT.jar
COPY ${JAR_FILE} application.jar
RUN java -Djarmode=tools -jar application.jar extract --layers --launcher --destination extracted

# Stage 2: Alpine JRE 21 runtime (JRE base + application layers)
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

# Heap sized from the cgroup memory limit, leaving ~40% for Metaspace, Code Cache, thread stacks and buffers.
# GC is set explicitly: with a 1 CPU limit JVM ergonomics would silently fall back to SerialGC.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=60.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

EXPOSE 8080

# Use exec to ensure JVM process receives SIGTERM directly as PID 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
