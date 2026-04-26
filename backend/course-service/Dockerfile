FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /app

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

COPY pom.xml .
RUN mvn dependency:resolve -B || mvn dependency:resolve -B -U

COPY src ./src
RUN mvn clean package -DskipTests -B -X

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app
RUN apk add --no-cache curl su-exec ffmpeg && addgroup -S spring && adduser -S spring -G spring
COPY --from=builder /app/target/*.jar app.jar
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh
RUN chown -R spring:spring /app
EXPOSE 8003
ENTRYPOINT ["docker-entrypoint.sh"]
