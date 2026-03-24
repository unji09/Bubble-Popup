FROM bde2020/spark-master:3.1.1-hadoop3.2 AS master

RUN apk add --no-cache python3 py3-pip && \
    pip3 install --no-cache-dir pymysql

RUN curl -L -o /spark/jars/mysql-connector-j-8.4.0.jar \
    https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar && \
    curl -L -o /spark/jars/spark-excel_2.12-3.1.1_0.18.5.jar \
    https://repo1.maven.org/maven2/com/crealytics/spark-excel_2.12/3.1.1_0.18.5/spark-excel_2.12-3.1.1_0.18.5.jar

FROM bde2020/spark-worker:3.1.1-hadoop3.2 AS worker

RUN curl -L -o /spark/jars/mysql-connector-j-8.4.0.jar \
    https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.4.0/mysql-connector-j-8.4.0.jar && \
    curl -L -o /spark/jars/spark-excel_2.12-3.1.1_0.18.5.jar \
    https://repo1.maven.org/maven2/com/crealytics/spark-excel_2.12/3.1.1_0.18.5/spark-excel_2.12-3.1.1_0.18.5.jar
