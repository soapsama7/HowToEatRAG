# =====================================================================
# MCP Tool Server 镜像（构建前置：宿主机先执行 mvn -pl howtocook-mcp-server -am package）
# 构建上下文为 backend/（compose 中 context: ..）
# 注：本机 Docker Hub 拉取受限，基础镜像用本地已有的 eclipse-temurin:21-jdk
#     （JRE 21 可运行 Java 17 编译产物）；有网环境可改回 eclipse-temurin:17-jre
# =====================================================================
FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY howtocook-mcp-server/target/howtocook-mcp-server-*.jar app.jar

EXPOSE 18082

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
