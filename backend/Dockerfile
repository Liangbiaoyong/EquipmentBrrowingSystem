# 第一阶段：构建可执行 JAR
FROM maven:3.8-openjdk-17-slim AS build
WORKDIR /app
COPY pom.xml .
# 预下载依赖（仅解析主依赖，跳过插件）
RUN mvn dependency:resolve -B || true
COPY src ./src
RUN mvn clean package -DskipTests

# 第二阶段：运行 JRE
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
