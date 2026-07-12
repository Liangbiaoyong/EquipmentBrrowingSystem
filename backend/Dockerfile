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
# 安装 mysql-client（供备份功能使用 mysqldump）
RUN apk add --no-cache mysql-client
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
