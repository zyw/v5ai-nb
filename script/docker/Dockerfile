# syntax=docker/dockerfile:1

# 两阶段 Dockerfile：在构建阶段编译项目，在运行阶段只保留 JRE 和应用 jar。
# 构建上下文必须是仓库根目录：
# docker build -f script/docker/Dockerfile -t v5ai-nb:latest .
FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /build
COPY . .

# 项目默认跳过测试；镜像构建只负责生成可运行 jar。
RUN mvn -DskipTests -Dmaven.test.skip=true package

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

COPY --from=builder /build/v5ai-starter/target/v5ai-starter-${APP_VERSION}.jar /app/v5ai-nb.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=5 \
  CMD wget --no-verbose --tries=1 --spider http://127.0.0.1:8080/api/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/v5ai-nb.jar --spring.config.additional-location=optional:file:/app/config/"]
