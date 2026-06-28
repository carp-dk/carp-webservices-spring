FROM gradle:8.14-jdk21 AS builder
COPY ./ /src
USER root
WORKDIR /src
RUN gradle clean bootJar -x test

FROM amazoncorretto:21-alpine
WORKDIR /app
COPY --from=builder /src/build/libs/carp-platform.jar .

RUN chown -R root:root /app
USER root

ENTRYPOINT ["java", \
     "-XshowSettings:vm", \
     "-XX:+ExitOnOutOfMemoryError", "-XX:+HeapDumpOnOutOfMemoryError", "-XX:HeapDumpPath=/tmp", "-XX:ErrorFile=/tmp/jdk.error", \
     "-Dcom.sun.management.jmxremote", \
     "-jar", "/app/carp-platform.jar"]