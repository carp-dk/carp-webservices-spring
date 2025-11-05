FROM gradle:8.7-jdk17 AS builder
COPY ./ /src
USER root
WORKDIR /src
RUN gradle clean bootJar -x test

FROM amazoncorretto:17-alpine
WORKDIR /app
COPY --from=builder /src/build/libs/carp-platform.jar .

RUN chown -R root:root /app
USER root

ENTRYPOINT ["java","-jar","carp-platform.jar"]

# -XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap > lets the JVM respect CPU and RAM limits inside a Docker container
# -XX:+ExitOnOutOfMemoryError > JVM exits on the first occurrence of an out-of-memory error

CMD ["java", \
     "-XX:+UnlockExperimentalVMOptions", "-XX:+UseCGroupMemoryLimitForHeap", "-XshowSettings:vm", \
     "-XX:+ExitOnOutOfMemoryError","-XX:+HeapDumpOnOutOfMemoryError", "-XX:HeapDumpPath=/tmp","-XX:ErrorFile=/tmp/jdk.error", \
     "-XX:TieredStopAtLevel=1", \
     "-Djava.security.egd=file:/dev/./urandom", \
     "-Dcom.sun.management.jmxremote", \
     "-jar", "/app/carp-platform.jar"]