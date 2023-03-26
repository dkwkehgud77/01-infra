# Data Pipeline Setting

### Introduction
이 애플리케이션은 JSON 설정 파일을 읽어 AVRO 스키마, Kafka 토픽, 및 MySQL 테이블을 자동으로 생성합니다. 
데이터 파이프라인 설치 과정을 간소화하고 AVRO 스키마, Kafka 토픽 및 MySQL 테이블 간의 일관성을 보장할 수 있습니다.
이를 통해 시간을 절약하고 오류 위험을 줄이며 데이터 인프라를 유지 관리하고 확장하기 쉬워집니다.

### Features
- 테스트를 위한 Kafka, Schema-Registry, MySQL 등 Docker-compose로 생성
- 첨부된 Json 파일을 읽어 Avro Schema 포맷의 Json 파일로 변환하여 덤프 및 재사용
- Avro Schema 데이터를 파싱하여 Kafka Topic, MySQL Table을 동적으로 생성   
- 새로운 데이터 파이프라인을 관리하고 배포하는 작업에 시간을 절약하고 오류 위험을 감소 


# Getting Started
### Prerequisites
- Java JDK 11
- Apache Maven 3.9.0
- Apache Kafka 2.8.1
- MySQL 8.0
- Docker 20.10.17
- Docker-compose 1.29.2


### Infra Setting 
- Zookeeper, Kafka, Schema-Registry, MySQL 생성 및 실행 
- AVRO 스키마, Kafka 토픽, MySql 테이블 생성

```bash
$ docker-compose up -d 
Starting mysql     ... done
Starting zookeeper ... done
Starting kafka2    ... done
Starting kafka1    ... done
Starting kafka3    ... done
Starting schema-registry ... done
Starting kafdrop         ... done

$ docker ps
CONTAINER ID   IMAGE                                   COMMAND                  CREATED        STATUS        PORTS                                                  NAMES
b0ef08a377b2   obsidiandynamics/kafdrop                "/kafdrop.sh"            13 hours ago   Up 13 hours   0.0.0.0:9000->9000/tcp                                 kafdrop
9a23d9188cf1   confluentinc/cp-schema-registry:7.0.0   "/etc/confluent/dock…"   13 hours ago   Up 13 hours   0.0.0.0:8081->8081/tcp                                 schema-registry
c37a1e5ab2ba   confluentinc/cp-kafka:7.0.0             "/etc/confluent/dock…"   13 hours ago   Up 13 hours   9092/tcp, 0.0.0.0:9093->9093/tcp                       kafka3
a9e70028d48e   confluentinc/cp-kafka:7.0.0             "/etc/confluent/dock…"   13 hours ago   Up 13 hours   0.0.0.0:9091->9091/tcp, 9092/tcp                       kafka1
2161366c7af8   confluentinc/cp-kafka:7.0.0             "/etc/confluent/dock…"   13 hours ago   Up 13 hours   0.0.0.0:9092->9092/tcp                                 kafka2
e4378c3e42ca   mysql:8.0                               "docker-entrypoint.s…"   2 days ago     Up 13 hours   0.0.0.0:3306->3306/tcp, 33060/tcp                      mysql
9a4e488a016e   zookeeper:3.7                           "/docker-entrypoint.…"   2 days ago     Up 13 hours   2888/tcp, 3888/tcp, 0.0.0.0:2181->2181/tcp, 8080/tcp   zookeeper
```


### Configuration
프로퍼티 파일에 Kafka Consumer 애플리케이션에 필요한 구성 설정을 합니다.
```properties
# Json
before.json.file.path=../schema/schema_before.json
after.json.file.path=../schema/schema_avro.json

# Kafka
kafka.bootstrap.servers=localhost:9091,localhost:9092,localhost:9093
kafka.topic.partition.count=3
kafka.topic.replica-factor.count=3

# MySQL
mysql.url=jdbc:mysql://localhost:3306/bank
mysql.username=infra
mysql.password=infra1!
```

### Application Start
1. properties 파일을 세팅합니다.
2. Maven을 사용하여 프로젝트를 빌드합니다.
3. Maven을 사용하여 Consumer 애플리케이션을 실행합니다.
```bash
$ mvn clean compile
$ mvn exec:java

[main] INFO Main - data pipeline setting start...
[main] INFO com.exam.worker.DataPipeline - Avro schema created succeessfully ... dataset1
[main] INFO com.exam.worker.DataPipeline - Avro schema created succeessfully ... dataset2
[main] INFO com.exam.worker.DataPipeline - Avro schema created succeessfully ... dataset3
[main] INFO com.exam.worker.DataPipeline - Avro schema Json dumped succeessfully ...
```

### Application Deployment
1. properties 파일을 세팅합니다.
2. Maven을 사용하여 프로젝트를 패키징합니다.
3. Java를 사용하여 백그라운드로 Producer 애플리케이션을 실행합니다.
```bash
$ mvn clean compile
$ nohup java -jar target/producer-1.0-jar-with-dependencies.jar &

[1]  + exit 1     nohup java -jar target/producer-1.0-jar-with-dependencies.jar

$ ps -ef |grep infra
  501 13165 10580   0  1:56PM ttys018    0:01.93 /usr/bin/java -jar target/infra-1.0-jar-with-dependencies.jar
  501 13181 10580   0  1:56PM ttys018    0:00.00 grep infra
```

### Application Stop
Mac이나 리눅스 기반의 OS에서는 Shell 파일을 이용해서 애플리케이션을 실행하거나 중지할 수 있습니다.
```bash
$ chmod 755 start.sh stop.sh
$ ./stop.sh

[1]  + killed     nohup java -jar target/infra-1.0-jar-with-dependencies.jar
```

