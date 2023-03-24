
# 인프라

### 1. infra 세팅
- Zookeeper, Kafka, Schema-Registry, MySQL 생성
- AVRO 스키마, Kafka 토픽, MySql 테이블 생성

```bash
cd pipeline/01-infra
docker-compose up -d 

mvn clean compile
mvn exec:java

mvn clean package
java -jar infra-1.0-SNAPSHOT-jar
```

USE mysql;
CREATE USER 'infra'@'%' IDENTIFIED BY 'infra1!';
GRANT ALL PRIVILEGES ON *.* TO 'infra'@'%';
FLUSH PRIVILEGES;


USE mysql;
CREATE USER 'consumer'@'%' IDENTIFIED BY 'consumer1!';
GRANT ALL PRIVILEGES ON *.* TO 'consumer'@'%'; 
FLUSH PRIVILEGES;