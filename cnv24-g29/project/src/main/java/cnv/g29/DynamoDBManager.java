package cnv.g29;

import java.util.Map;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.services.dynamodbv2.util.TableUtils;

import static cnv.g29.AWSConstants.*;

/**
 * This sample demonstrates how to perform a few simple operations with the
 * Amazon DynamoDB service.
 */
public class DynamoDBManager {

    public static final String PRIMARY_HASH_KEY = "id";
    private AmazonDynamoDB dynamoDB;

    public DynamoDBManager() {
        this.dynamoDB = AmazonDynamoDBClientBuilder.standard()
                .withCredentials((AWSCredentialsProvider) DEFAULT_CREDENTIALS_PROVIDER)
                .withRegion(AWSConstants.AWS_DEFAULT_REGION_STR)
                .build();
    }

    public void describeTable(String tableName) {
        // Create a table with a primary hash key named 'name', which holds a string
        CreateTableRequest createTableRequest = new CreateTableRequest().withTableName(tableName)
                .withKeySchema(new KeySchemaElement().withAttributeName(PRIMARY_HASH_KEY).withKeyType(KeyType.HASH))
                .withAttributeDefinitions(new AttributeDefinition().withAttributeName(PRIMARY_HASH_KEY).withAttributeType(ScalarAttributeType.S))
                .withProvisionedThroughput(new ProvisionedThroughput().withReadCapacityUnits(1L).withWriteCapacityUnits(1L));

        // Create table if it does not exist yet
        TableUtils.createTableIfNotExists(dynamoDB, createTableRequest);
        // wait for the table to move into ACTIVE state
        try {
            TableUtils.waitUntilActive(dynamoDB, tableName);
        } catch (InterruptedException ase) {
            System.out.println("Caught an InterruptedException.");
            System.out.println("Error Message:    " + ase.getMessage());
        }
        // Describe our new table
        DescribeTableRequest describeTableRequest = new DescribeTableRequest().withTableName(tableName);
        TableDescription tableDescription = dynamoDB.describeTable(describeTableRequest).getTable();
        System.out.println("Table Description: " + tableDescription);
    }

    public void addItemToTable(String tableName, Map<String, AttributeValue> item) {
        dynamoDB.putItem(new PutItemRequest(tableName, item));
    }

    public GetItemResult getItem(String tableName, Map<String, AttributeValue> condition){
        return dynamoDB.getItem(new GetItemRequest(tableName, condition));
    }


//    public void searchItemInTable(String tableName, HashMap<String, Condition> scanFilter) {
//        // Scan items for movies with a year attribute greater than 1985
////        HashMap<String, Condition> scanFilter = new HashMap<String, Condition>();
////        Condition condition = new Condition()
////                .withComparisonOperator(ComparisonOperator.GT.toString())
////                .withAttributeValueList(new AttributeValue().withN("1985"));
////        scanFilter.put("year", condition);
//        ScanRequest scanRequest = new ScanRequest(tableName).withScanFilter(scanFilter);
//        ScanResult scanResult = dynamoDB.scan(scanRequest);
//        System.out.println("Result: " + scanResult);
//    }
//
//    private static Map<String, AttributeValue> newItem(String name, int year, String rating, String... fans) {
//        Map<String, AttributeValue> item = new HashMap<String, AttributeValue>();
//        item.put("name", new AttributeValue(name));
//        item.put("year", new AttributeValue().withN(Integer.toString(year)));
//        item.put("rating", new AttributeValue(rating));
//        item.put("fans", new AttributeValue().withSS(fans));
//        return item;
//    }

}
