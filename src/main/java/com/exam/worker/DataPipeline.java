package com.exam.worker;

import org.apache.avro.Schema;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListTopicsResult;
import org.apache.kafka.clients.admin.NewTopic;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ExecutionException;

public class DataPipeline {
    private static final ResourceBundle RESOURCE_BUNDLE = ResourceBundle.getBundle("config");
    private final Logger logger = LoggerFactory.getLogger(DataPipeline.class);

    public List<Schema> createSchema(JSONArray beforeJsonArray) throws IOException {
        JSONArray avroJsonArray = new JSONArray();
        List<Schema> avroSchemaList = new ArrayList<>();

        // Avro 스키마 Json 파일을 생성하고, Schema 리스트로 반환합니다.
        try {
            for(Object obj : beforeJsonArray){
                // 기존 Json 객체에서 Avro 포맷의 Json 객체로 변환합니다.
                JSONObject beforeSchemaJson = (JSONObject) obj;
                JSONObject avroSchemaJson = new JSONObject();

                avroSchemaJson.put("type", "record");
                String SchemaName = String.valueOf(beforeSchemaJson.get("name"));
                avroSchemaJson.put("name", SchemaName);
                avroSchemaJson.put("namespace", "com.exam");

                JSONObject fieldsObject = (JSONObject)beforeSchemaJson.get("fields");
                JSONArray fieldsArray = new JSONArray();
                for (Object entryObj : fieldsObject.entrySet()) {
                    Map.Entry<String, String> entry = (Map.Entry<String, String>) entryObj;
                    JSONObject field = new JSONObject();
                    field.put("name", entry.getKey());
                    field.put("type", entry.getValue().replace("integer","int"));
                    fieldsArray.add(field);
                }
                avroSchemaJson.put("fields", fieldsArray);

                // Avro 스키마로 생성되는지 확인하고 리스트에 담습니다.
                Schema avroSchema = new Schema.Parser().parse(avroSchemaJson.toJSONString());
                avroSchemaList.add(avroSchema);
                logger.info("Avro schema created succeessfully ... " + SchemaName);
                avroJsonArray.add(avroSchemaJson);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        // Avro 포맷의 Json 파일로 덤프합니다. -> Kafka 프로듀서에서 Avro 스키마 로드할 때 사용됩니다.
        String jsonFilePath = RESOURCE_BUNDLE.getString("after.json.file.path");
        try(FileWriter avroJsonFile = new FileWriter(jsonFilePath)){
            avroJsonFile.write(avroJsonArray.toJSONString());
            avroJsonFile.flush();
            logger.info("Avro schema Json dumped succeessfully ...");
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        return avroSchemaList;
    }

    public void createTopic(List<Schema> avroSchemaList) {
        // 토픽 파티션, 리플리카 팩터 개수를 설정합니다.
        final int numPartitions = Integer.parseInt(RESOURCE_BUNDLE.getString("kafka.topic.partition.count"));  // 토픽 파티션 수
        final int replicationFactor = Integer.parseInt(RESOURCE_BUNDLE.getString("kafka.topic.replica-factor.count"));  // 리플리카 팩터 수

        // Kafka 토픽을 생성하기 위한 AdminClient 객체를 생성합니다.
        Properties props = new Properties();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, RESOURCE_BUNDLE.getString("kafka.bootstrap.servers"));

        try (AdminClient adminClient = AdminClient.create(props)) {
            // Avro 스키마의 Name 으로 Kafka 토픽을 생성합니다.
            for(Schema avroSchema : avroSchemaList) {
                String topicName = avroSchema.getName();

                // 토픽이 존재하는지 확인하고 있으면 삭제합니다.
                ListTopicsResult listTopicsResult = adminClient.listTopics();
                Set<String> topicNames = listTopicsResult.names().get();
                if (topicNames.contains(topicName)) {
                    adminClient.deleteTopics(Collections.singleton(topicName)).all().get();
                    logger.info("Topic has been deleted... " + topicName);
                }
                // 지정한 Partition 개수와 Replication Factor 개수로 토픽을 생성합니다.
                NewTopic newTopic = new NewTopic(topicName, numPartitions, (short) replicationFactor);
                adminClient.createTopics(Collections.singletonList(newTopic));

                listTopicsResult = adminClient.listTopics();
                topicNames = listTopicsResult.names().get();
                if (topicNames.contains(topicName)){
                    logger.info("Topic %s created successfully... " + topicName);
                }


// 해당 토픽 삭제
//                adminClient.deleteTopics(Collections.singleton(topicName)).all().get();

//                // 토픽이 있으면 삭제하고 토픽 생성
//                ListTopicsResult listTopicsResult = adminClient.listTopics();
//                Set<String> topicNames = listTopicsResult.names().get();
//                while (true){
//                    if (!topicNames.contains(topicName)) {
//                        logger.info("Topic %s has been deleted.\n", topicName);
//
//                        // 토픽 생성
//                        NewTopic newTopic = new NewTopic(topicName, numPartitions, (short) replicationFactor);
//                        adminClient.createTopics(Collections.singletonList(newTopic));
//
//                        topicNames = listTopicsResult.names().get();
//                        if (topicNames.contains(topicName)){
//                            logger.info("Topic created successfully... " + topicName);
//                            break;
//                        }
//                    }
//                }

            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

    }

    public void createTable(List<Schema> avroSchemaList) {
        Connection conn = null;
        Statement stmt = null;

        try {
            // MySQL 커넥션을 연결합니다.
            Class.forName("com.mysql.cj.jdbc.Driver");
            conn = DriverManager.getConnection(
                    RESOURCE_BUNDLE.getString("mysql.url"),
                    RESOURCE_BUNDLE.getString("mysql.username"),
                    RESOURCE_BUNDLE.getString("mysql.password")
            );

            // Database 를 생성합니다.
            stmt = conn.createStatement();
            String sql_database_drop = "DROP DATABASE IF EXISTS bank";
            stmt.executeUpdate(sql_database_drop);
            String sql_database = "CREATE DATABASE bank";
            stmt.executeUpdate(sql_database);
            stmt.execute("USE bank");

            // Kafka Offset 을 관리하기 위한 테이블을 생성합니다.
            String sql_offsets_drop = "DROP TABLE IF EXISTS kafka_offsets";
            stmt.executeUpdate(sql_offsets_drop);
            String sql_offsets = "CREATE TABLE kafka_offsets (\n" +
                    "    topic     varchar(50) not null,\n" +
                    "    `partition` int          not null,\n" +
                    "    consumer_group    varchar(50) ,\n" +
                    "    offset    bigint       ,\n" +
                    "    primary key (topic, `partition`, consumer_group)\n" +
                    ");";
            stmt.executeUpdate(sql_offsets);

            // Avro 스키마 포맷을 기반으로 최종적으로 Kafka 메시지를 저장할 테이블을 생성합니다.
            for(Schema avroSchema : avroSchemaList) {

                // Avro 스키마에서 Table 명과 Filed 리스트를 가져옵니다.
                String tableName = avroSchema.getName();
                List<Schema.Field> fields = avroSchema.getFields();

                // Avro 스키마를 기반으로 동적으로 MySQL 테이블 생성 SQL 구문을 생성합니다.
                String sql_table_drop = "DROP TABLE IF EXISTS " + tableName;
                stmt.executeUpdate(sql_table_drop);
                StringBuilder sql_table = new StringBuilder("CREATE TABLE " + tableName + " (");
                // Filed 리스트에서 Filed 명, Field 타입을 가져와서 MySQL DDL 포맷으로 변환합니다.
                for (Schema.Field field : fields) {
                    String name = field.name();
                    Schema.Type type = field.schema().getType();
                    String sqlType = "";
                    switch (type) {
                        case BOOLEAN:
                            sqlType = "BOOLEAN";
                            break;
                        case INT:
                            sqlType = "INT";
                            break;
                        case LONG:
                            sqlType = "BIGINT";
                            break;
                        case FLOAT:
                            sqlType = "FLOAT";
                            break;
                        case DOUBLE:
                            sqlType = "DOUBLE";
                            break;
                        case STRING:
                            sqlType = "VARCHAR(255)";
                            break;
                        default:
                            // TODO: handle other data types if necessary
                            break;
                    }
                    sql_table.append(name).append(" ").append(sqlType).append(",");
                }
                sql_table.deleteCharAt(sql_table.length() - 1);
                sql_table.append(");");

                // 최종적으로 데이터가 적재될 테이블을 생성합니다.
                stmt.executeUpdate(sql_table.toString());

                System.out.println("Table created successfully... " + tableName);
            }
        } catch (SQLException | ClassNotFoundException e) {
            e.printStackTrace();
        } finally {
            // 작업이 끝나면 Statement 와 Connection 객체를 반환합니다.
            try {
                if (stmt != null)
                    stmt.close();
                if (conn != null)
                    conn.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }

        }
    }
}
