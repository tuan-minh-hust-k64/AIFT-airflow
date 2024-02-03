package com.ikame.aift.gcp;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.bigquery.*;
import com.google.cloud.bigquery.BigQuery.QueryResultsOption;
import lombok.extern.slf4j.Slf4j;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

@Slf4j
public class BigqueryHelper {
    private final static BigQuery bigQuery;

    static {
        try {
            bigQuery = BigQueryOptions.newBuilder()
                    .setCredentials(GoogleCredentials.fromStream(Objects.requireNonNull(BigqueryHelper.class.getResourceAsStream("/genai_key.json")))).build().getService();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static TableResult bigqueryCommand(String queryString) {

        QueryJobConfiguration queryJobConfiguration = QueryJobConfiguration.newBuilder(queryString)
                .setUseLegacySql(false).build();
        JobId jobId = JobId.of(UUID.randomUUID().toString());
        Job queryJob = bigQuery.create(JobInfo.newBuilder(queryJobConfiguration).setJobId(jobId).build());
        try {
            queryJob = queryJob.waitFor();
            if (queryJob == null) {
                throw new RuntimeException("Job no longer exists");
            } else if(queryJob.getStatus().getError() != null) {
                throw new RuntimeException(queryJob.getStatus().getError().toString());
            }
            return queryJob.getQueryResults(QueryResultsOption.pageSize(100));
        } catch (InterruptedException e) {
            log.error("ERROR: {}", e.getMessage());
            throw new RuntimeException("Can not query in bigquery!");
        }
    }

    public static void tableInsertRows(
            String datasetName, String tableName, Iterable<InsertAllRequest.RowToInsert> rowsContent) {
        try {
            TableId tableId = TableId.of(datasetName, tableName);
            BigQuery bigQueryInsert = BigQueryOptions.newBuilder()
                    .setCredentials(GoogleCredentials.fromStream(Objects.requireNonNull(BigqueryHelper.class.getResourceAsStream("/genai_key.json")))).build().getService();
            InsertAllResponse response =
                    bigQueryInsert.insertAll(
                            InsertAllRequest.newBuilder(tableId)
                                    .setRows(rowsContent)
                                    .build());

            if (response.hasErrors()) {
                for (Map.Entry<Long, List<BigQueryError>> entry : response.getInsertErrors().entrySet()) {
                    log.error("Response error: \n" + entry.getValue());
                }
            }
            log.info("Rows successfully inserted into table");
        } catch (BigQueryException e) {
            log.error("Insert operation not performed \n" + e.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public void runCreateTable(String datasetName, String tableName, Field... column) {
        Schema schema =
                Schema.of(
//                        Field.of("review_text", StandardSQLTypeName.STRING),
//                        Field.of("label", StandardSQLTypeName.STRING),
//                        Field.of("operating_system", StandardSQLTypeName.STRING),
//                        Field.of("event_date", StandardSQLTypeName.DATE),
//                        Field.of("event_timestamp", StandardSQLTypeName.TIMESTAMP),
//                        Field.of("month", StandardSQLTypeName.STRING),
//                        Field.of("review_date", StandardSQLTypeName.DATETIME),
//                        Field.of("app_package_name", StandardSQLTypeName.STRING),
//                        Field.of("app_version_code", StandardSQLTypeName.STRING),
//                        Field.of("app_version_name", StandardSQLTypeName.STRING),
//                        Field.of("reviewer_language", StandardSQLTypeName.STRING),
//                        Field.of("device", StandardSQLTypeName.STRING),
//                        Field.of("star_rating", StandardSQLTypeName.INT64),
//                        Field.of("review_text", StandardSQLTypeName.STRING),
//                        Field.of("review_link", StandardSQLTypeName.STRING),
//                        Field.of("developer_reply_date", StandardSQLTypeName.DATETIME),
//                        Field.of("developer_reply_text", StandardSQLTypeName.STRING),
//                        Field.of("resource_name", StandardSQLTypeName.STRING),
//                        Field.of("review_text_translated", StandardSQLTypeName.STRING),
//                        Field.of("label", StandardSQLTypeName.STRING)
                        column
                );

        createTable(datasetName, tableName, schema);
    }

    public void createTable(String datasetName, String tableName, Schema schema) {
        try {
            // Initialize client that will be used to send requests. This client only needs to be created
            // once, and can be reused for multiple requests
            BigQuery bigQueryInsert = BigQueryOptions.newBuilder()
                    .setCredentials(GoogleCredentials.fromStream(new FileInputStream("src/main/resources/genai_key.json"))).build().getService();
            TableId tableId = TableId.of(datasetName, tableName);
            TableDefinition tableDefinition = StandardTableDefinition.of(schema);
            TableInfo tableInfo = TableInfo.newBuilder(tableId, tableDefinition).build();
            bigQueryInsert.create(tableInfo);
            System.out.println("Table created successfully");
        } catch (BigQueryException e) {
            System.out.println("Table was not created. \n" + e.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public void deleteTable(String datasetName, String tableName) {
        try {
            // Initialize client that will be used to send requests. This client only needs to be created
            // once, and can be reused for multiple requests.
            BigQuery bigQueryInsert = BigQueryOptions.newBuilder()
                    .setCredentials(GoogleCredentials.fromStream(new FileInputStream("src/main/resources/genai_key.json"))).build().getService();
            boolean success = bigQueryInsert.delete(TableId.of(datasetName, tableName));
            if (success) {
                System.out.println("Table deleted successfully");
            } else {
                System.out.println("Table was not found");
            }
        } catch (BigQueryException e) {
            System.out.println("Table was not deleted. \n" + e.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public void addEmptyColumn() {
        try {
            BigQuery bigquery = BigQueryOptions.newBuilder()
                    .setCredentials(GoogleCredentials.fromStream(new FileInputStream("src/main/resources/genai_key.json"))).build().getService();

            Table table = bigquery.getTable("AllProject_OverviewMetric", "GOOGLE_PLAYSTORE_play_store_labeled_review");
            Schema schema = table.getDefinition().getSchema();
            assert schema != null;
            FieldList fields = schema.getFields();

            // Create the new field/column
            Field newField = Field.of("probability_label", LegacySQLTypeName.FLOAT);

            // Create a new schema adding the current fields, plus the new one
            List<Field> fieldList = new ArrayList<Field>();
            fields.forEach(fieldList::add);
            fieldList.add(newField);
            Schema newSchema = Schema.of(fieldList);

            // Update the table with the new schema
            Table updatedTable =
                    table.toBuilder().setDefinition(StandardTableDefinition.of(newSchema)).build();
            updatedTable.update();
            System.out.println("Empty column successfully added to table");
        } catch (BigQueryException e) {
            System.out.println("Empty column was not added. \n" + e.toString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
