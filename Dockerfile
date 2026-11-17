# 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY backend backend
COPY frontend frontend
RUN mvn -B -f backend/pom.xml package
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN mkdir /app/var && chown -R 10001:10001 /app
COPY --from=build /src/backend/target/app.jar /app/app.jar
USER 10001:10001
ENV BIND_ADDRESS=0.0.0.0
ENTRYPOINT ["java","-jar","/app/app.jar"]
