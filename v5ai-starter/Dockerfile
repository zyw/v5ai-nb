# syntax=docker/dockerfile:1

# 该 Dockerfile 只负责将已构建的 jar 打包成运行镜像。
# 使用前先在仓库根目录执行：
# mvn -pl v5ai-starter -am package -DskipTests
# 然后执行：
# docker build -f v5ai-starter/Dockerfile -t v5ai-nb:latest v5ai-starter
FROM eclipse-temurin:21-jre-jammy

ARG APP_VERSION=0.1.0-SNAPSHOT
ENV TZ=Asia/Shanghai \
    SPRING_PROFILES_ACTIVE=dev \
    JAVA_OPTS="-Xms512m -Xmx1024m"

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /app/config /app/logs /data/uploads /data/agentscope/workspace /data/agentscope/state

COPY target/v5ai-starter-${APP_VERSION}.jar /app/v5ai-nb.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:8080/api/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/v5ai-nb.jar --spring.config.additional-location=optional:file:/app/config/"]
