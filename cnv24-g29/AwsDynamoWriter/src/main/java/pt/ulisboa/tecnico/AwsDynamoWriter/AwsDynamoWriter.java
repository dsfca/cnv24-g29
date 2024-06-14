package pt.ulisboa.tecnico.AwsDynamoWriter;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.services.dynamodbv2.util.TableUtils;

import static pt.ulisboa.tecnico.AwsDynamoWriter.AWSConstants.*;




public class AwsDynamoWriter {
	public static final String PRIMARY_HASH_KEY = "id";
    private AmazonDynamoDB dynamoDB;
    
    public AwsDynamoWriter() {
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
    
    public long getLastId(String tableName) {
    	List<Map<String, AttributeValue>> scanResult = scanTable(tableName);
        
    	// Find the item with the highest ID
        //long ans = 1;
        long ans = 0;

        for (Map<String, AttributeValue> item : scanResult) {
            long currentId = Long.parseLong(item.get("id").getS());
            if (currentId > ans) {
                ans = currentId;
            }
        }
    	return ans;
    }

    public void addItemToTable(String tableName, Map<String, AttributeValue> item) {
    	System.out.println(item.toString());
    	long lastId = getLastId(tableName) + 1;
    	String nextId = String.format("%02d", lastId);
    	item.put("id", new AttributeValue(nextId));
        dynamoDB.putItem(new PutItemRequest(tableName, item));
    }
    

    public GetItemResult getItem(String tableName, Map<String, AttributeValue> condition){
        return dynamoDB.getItem(new GetItemRequest(tableName, condition));
    }
    
    public List<Map<String, AttributeValue>> scanTable(String tableName) {
            ScanRequest scanRequest = new ScanRequest()
                    .withTableName(tableName);
            ScanResult result;
            List<Map<String, AttributeValue>> ans = new ArrayList<Map<String,AttributeValue>>();
            do {
                result = dynamoDB.scan(scanRequest);
                /*for (Map<String, AttributeValue> item : result.getItems()) {
                    printItem(item);
                }*/
                ans.addAll(result.getItems());
                
                // Update the scan request with the last evaluated key
                scanRequest.setExclusiveStartKey(result.getLastEvaluatedKey());
            } while (result.getLastEvaluatedKey() != null);
            
            return ans;
        
    }
    
    public void deleteItem(String tableName, Map<String, AttributeValue> item) {
        // Create the delete item request
        Map<String, AttributeValue> key = new HashMap<>();
        key.put("id", item.get("id")); // Replace with actual sort key name

        DeleteItemRequest deleteRequest = new DeleteItemRequest()
                .withTableName(tableName)
                .withKey(key);

        // Execute the delete request
        dynamoDB.deleteItem(deleteRequest);
    }
    
    public List<Map<String, AttributeValue>> getLastNItems(String tableName, long fromId){
    	List<Map<String, AttributeValue>> scanResult = scanTable(tableName);
    	List<Map<String, AttributeValue>> ans = new ArrayList<Map<String, AttributeValue>>();
    	
    	for(Map<String, AttributeValue> item : scanResult) {
    		long itemId = Long.parseLong(item.get("id").getS());
    		if (itemId >= fromId) {
    			ans.add(item);
    		}
    	}
    	return ans;
    }
    
    public static synchronized void writeMetricsToDynamoDb(Map<String, AttributeValue> collectedMetrics, String tableName) {
    	AwsDynamoWriter dynamoWriter = new AwsDynamoWriter();
    	dynamoWriter.describeTable(tableName);
    	dynamoWriter.addItemToTable(tableName, collectedMetrics);
    }
    
    public static synchronized List<Map<String, AttributeValue>> getlastNItemsStatic(String tableName, long fromId){
    	AwsDynamoWriter dWriter = new AwsDynamoWriter();
    	return dWriter.getLastNItems(tableName, fromId);
    }

}
