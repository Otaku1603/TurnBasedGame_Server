# Stage 1: Build
FROM maven:3.9.12-sapmachine-21 AS builder
WORKDIR /build

# 创建阿里云镜像配置
RUN echo '<?xml version="1.0" encoding="UTF-8"?> \
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0" \
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" \
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 http://maven.apache.org/xsd/settings-1.0.0.xsd"> \
  <mirrors> \
    <mirror> \
      <id>aliyunmaven</id> \
      <mirrorOf>*</mirrorOf> \
      <name>Aliyun</name> \
      <url>https://maven.aliyun.com/repository/public</url> \
    </mirror> \
  </mirrors> \
</settings>' > /usr/share/maven/conf/settings.xml

COPY pom.xml .
RUN mvn dependency:go-offline -B -s /usr/share/maven/conf/settings.xml
COPY src ./src
RUN mvn clean package -DskipTests -s /usr/share/maven/conf/settings.xml


# Stage 2: Run
# FROM eclipse-temurin:21-jdk
FROM eclipse-temurin:21-jre
WORKDIR /app

# 1. 拷贝 JAR 包
COPY --from=builder /build/target/*.jar app.jar

# 2. 将源码里的 Lua 脚本拷贝到容器内的“分发目录”
# 这是你的“种子库”
RUN mkdir -p /app/lua_dist
COPY src/main/resources/lua/*.lua /app/lua_dist/

# 3. 拷贝启动脚本并赋予执行权限
COPY entrypoint.sh /app/entrypoint.sh
RUN chmod +x /app/entrypoint.sh

# 4. 暴露端口
EXPOSE 8080 9999

# 5. 设置入口点为脚本
ENTRYPOINT ["/app/entrypoint.sh"]

# 6. 默认命令启动 Java
CMD ["java", "--add-opens", "java.base/java.net=ALL-UNNAMED", "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED", "-jar", "app.jar"]