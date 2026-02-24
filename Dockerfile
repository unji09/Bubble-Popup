# 기존 openjdk 대신 더 안정적인 eclipse-temurin 사용
FROM eclipse-temurin:17-jdk-alpine

# 빌드된 jar 파일 복사
COPY build/libs/*.jar app.jar

# 실행
ENTRYPOINT ["java", "-jar", "/app.jar"]

