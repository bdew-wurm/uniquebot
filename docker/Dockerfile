FROM eclipse-temurin:25.0.1_8-jre-noble

WORKDIR /opt/wurm/uniques

RUN chown daemon:daemon /opt/wurm/uniques

USER daemon

COPY logging.properties logging.properties
ADD build/distributions/docker.tar ./

CMD ["java", "-Xms256m", "-Xmx512m", "-Djava.util.logging.config.file=logging.properties", "-jar", "uniquebot.jar"]