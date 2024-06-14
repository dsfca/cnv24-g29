package cnv.g29.test;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;

import java.util.HashMap;
import java.util.Map;

import cnv.g29.DynamoDBManager;

public class TestDynamoDB {

    public static final String CNV_DYNAMODB_METRICS_TABLE = "CNV-WEBSERVER-METRICS";

    public static void main(String[] args) {
        DynamoDBManager dynamoDBManager = new DynamoDBManager();

        // Describe the table structure
        dynamoDBManager.describeTable(CNV_DYNAMODB_METRICS_TABLE);

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", new AttributeValue("id1"));
        item.put("Title", new AttributeValue("Title value"));
        item.put("Year", new AttributeValue().withN(Integer.toString(1999)));

        // Add item to the table
        //dynamoDBManager.addItemToTable(CNV_DYNAMODB_METRICS_TABLE, item);

        // Search an item inside the table
        System.out.println(dynamoDBManager.getItem(CNV_DYNAMODB_METRICS_TABLE, Map.of("id", new AttributeValue("id1"))));
    }
}
