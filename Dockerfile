FROM amazoncorretto:21-al2-jdk
LABEL AUTHOR='Sergio Exposito'
LABEL EMAIL='sjexpos@gmail.com'

# ENV JAVA_XMS             <set initial Java heap size>
# ENV JAVA_XMX             <set maximum Java heap size>
# ENV PORT                 <port to run server>
# ENV MANAGEMENT_PORT
# ENV DATABASE_HOST        <postgres server host name>
# ENV DATABASE_PORT        <postgres server port>
# ENV DATABASE_SCHEMA      <postgres schema>
# ENV DATABASE_USER        <postgres username>
# ENV DATABASE_PASSWORD    <postgres password>

ADD target/*.jar /opt/openspec-demo.jar

RUN bash -c 'touch /opt/openspec-demo.jar'

RUN echo "#!/usr/bin/env bash" > /opt/entrypoint.sh && \
    echo "" >> /opt/entrypoint.sh && \
    echo "echo \"===============================================\" " >> /opt/entrypoint.sh && \
    echo "echo \"JAVA_XMS: \$JAVA_XMS \" " >> /opt/entrypoint.sh && \
    echo "echo \"JAVA_XMX: \$JAVA_XMX \" " >> /opt/entrypoint.sh && \
    echo "echo \"===============================================\" " >> /opt/entrypoint.sh && \
    echo "echo \"PORT: \$PORT \" " >> /opt/entrypoint.sh && \
    echo "echo \"MANAGEMENT_PORT: \$MANAGEMENT_PORT \" " >> /opt/entrypoint.sh && \
    echo "echo \"DATABASE_HOST: \$DATABASE_HOST \" " >> /opt/entrypoint.sh && \
    echo "echo \"DATABASE_PORT: \$DATABASE_PORT \" " >> /opt/entrypoint.sh && \
    echo "echo \"DATABASE_SCHEMA: \$DATABASE_SCHEMA \" " >> /opt/entrypoint.sh && \
    echo "echo \"DATABASE_USER: \$DATABASE_USER \" " >> /opt/entrypoint.sh && \
    echo "echo \"DATABASE_PASSWORD: \$DATABASE_PASSWORD \" " >> /opt/entrypoint.sh && \
    echo "echo \"===============================================\" " >> /opt/entrypoint.sh && \
    echo "" >> /opt/entrypoint.sh && \
    echo "java \
        -Xms\$JAVA_XMS -Xmx\$JAVA_XMX \
        -Dserver.port=\$PORT \
        -Dmanagement.server.port=\$MANAGEMENT_PORT \
        -Dspring.datasource.host=\$DATABASE_HOST \
        -Dspring.datasource.port=\$DATABASE_PORT \
        -Dspring.datasource.schema=\$DATABASE_SCHEMA \
        -Dspring.datasource.username=\$DATABASE_USER \
        -Dspring.datasource.password=\$DATABASE_PASSWORD \
        -jar /opt/openspec-demo.jar" >> /opt/entrypoint.sh

RUN chmod 755 /opt/entrypoint.sh

EXPOSE ${PORT}

ENTRYPOINT [ "/opt/entrypoint.sh" ]

