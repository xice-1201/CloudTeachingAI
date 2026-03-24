# 多阶段构建：构建阶段
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# 配置阿里云 Maven 镜像
RUN mkdir -p /root/.m2 && \
    echo '<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" \
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 \
          http://maven.apache.org/xsd/settings-1.0.0.xsd"> \
          <mirrors> \
            <mirror> \
              <id>aliyun</id> \
              <mirrorOf>central</mirrorOf> \
              <name>Aliyun Maven</name> \
              <url>https://maven.aliyun.com/repository/public</url> \
            </mirror> \
          </mirrors> \
        </settings>' > /root/.m2/settings.xml

# 复制 pom 文件
COPY pom.xml .

# 下载依赖
RUN mvn dependency:resolve -B || mvn dependency:resolve -B -U

# 复制源代码并构建
COPY src ./src
RUN mvn clean package -DskipTests -B -X

# 运行阶段
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 创建非 root 用户
RUN addgroup -S spring && adduser -S spring -G spring

# 复制构建产物
COPY --from=builder /app/target/*.jar app.jar

# 设置权限
RUN chown -R spring:spring /app

USER spring:spring

EXPOSE 8002

ENTRYPOINT ["java", "-jar", "app.jar"]