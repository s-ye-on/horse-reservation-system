# Horse backend image. Build context: ./backend
# 테스트는 이미지 빌드 전 별도 검증(mise run backend:check)에서 실행하므로 여기서는 제외한다.

FROM eclipse-temurin:21-jdk-noble AS builder

WORKDIR /workspace

# Gradle wrapper를 먼저 복사해 배포판 다운로드 레이어를 재사용한다.
COPY gradlew ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --version --no-daemon

COPY settings.gradle build.gradle ./
COPY config ./config
COPY src ./src

RUN ./gradlew bootJar --no-daemon -x test

# build/libs 에는 boot JAR 외에 *-plain.jar 가 함께 생길 수 있으므로
# 실행 가능한 JAR 하나만 고르고, 판단할 수 없으면 빌드를 실패시킨다.
RUN set -eu; \
	candidates="$(find build/libs -maxdepth 1 -type f -name '*.jar' \
		! -name '*-plain.jar' ! -name '*-sources.jar' ! -name '*-javadoc.jar' | sort)"; \
	count="$(printf '%s\n' "$candidates" | grep -c . || true)"; \
	if [ "$count" -ne 1 ]; then \
		echo "Expected exactly one executable Spring Boot jar in build/libs but found $count:" >&2; \
		find build/libs -maxdepth 1 -type f -name '*.jar' >&2; \
		exit 1; \
	fi; \
	cp "$candidates" /workspace/app.jar

FROM eclipse-temurin:21-jre-noble

# 애플리케이션 설정(Asia/Seoul)과 컨테이너 시간대를 일치시킨다.
# 베이스 이미지에 tzdata 와 curl(Compose healthcheck 용)이 이미 포함되어 있어 추가 설치는 하지 않는다.
ENV TZ=Asia/Seoul

# 비특권 사용자로 실행한다.
RUN useradd --system --create-home --shell /usr/sbin/nologin horse

WORKDIR /app
COPY --from=builder /workspace/app.jar /app/app.jar

USER horse
EXPOSE 8080

# exec 형식으로 실행해 JVM 이 PID 1 이 되고 종료 시그널을 직접 받게 한다.
# 힙 크기 등 추가 옵션은 배포 서버 사양에 맞춰 JAVA_TOOL_OPTIONS 로 주입한다.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
