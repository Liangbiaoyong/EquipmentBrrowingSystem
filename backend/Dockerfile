# 第一阶段：构建可执行 JAR
FROM maven:3.8-openjdk-17-slim AS build
WORKDIR /app
COPY pom.xml .
# 下载依赖（利用 Docker 缓存层，pom.xml 不变则不重跑）
RUN mvn dependency:resolve -B 2>/dev/null || true
COPY src ./src
RUN mvn clean package -DskipTests

# 第二阶段：运行 JRE
FROM eclipse-temurin:17-jre-alpine
# tzdata 提供 Asia/Shanghai 时区数据；mysql-client（备份）+ nodejs（CAS DES加密）
RUN apk add --no-cache mysql-client nodejs tzdata
# 容器时区，决定 Java 默认时区（LocalDateTime.now() 等）与日志时间戳
ENV TZ=Asia/Shanghai
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# -Duser.timezone 与 TZ 双保险：基础镜像若未读取 TZ，JVM 仍固定在中国时区
ENTRYPOINT ["java", "-Duser.timezone=Asia/Shanghai", "-jar", "app.jar"]
