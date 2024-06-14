package pt.ulisboa.tecnico.AwsDynamoWriter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;

/**
 * Hello world!
 *
 */
public class App 
{
	public static final String CNV_DYNAMODB_METRICS_TABLE = "CNV-WEBSERVER-METRICS";
	
    public static void main( String[] args )
    {
    	AwsDynamoWriter dynamoWriter = new AwsDynamoWriter();

        // Describe the table structure
    	dynamoWriter.describeTable(CNV_DYNAMODB_METRICS_TABLE);

        /*Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", new AttributeValue("id1"));
        item.put("Title", new AttributeValue("Title value"));
        item.put("Year", new AttributeValue().withN(Integer.toString(1999)));*/

        // Add item to the table
        //dynamoWriter.addItemToTable(CNV_DYNAMODB_METRICS_TABLE, item);
        //Map<String, AttributeValue> map = new HashMap<String, AttributeValue>();
        //map.put("id", new AttributeValue("id1"));
        // Search an item inside the table
        //System.out.println(dynamoWriter.getItem(CNV_DYNAMODB_METRICS_TABLE, map));
    	//List<Map<String, AttributeValue>> scanResult = dynamoWriter.getLastNItems(CNV_DYNAMODB_METRICS_TABLE, 1);
    	/*for(Map<String, AttributeValue> item : scanResult)
    		dynamoWriter.deleteItem(CNV_DYNAMODB_METRICS_TABLE, item);*/
    	
    	//System.out.println(AwsDynamoWriter.getlastNItemsStatic(CNV_DYNAMODB_METRICS_TABLE, 81));
    	/*long lastId = dynamoWriter.getLastId(CNV_DYNAMODB_METRICS_TABLE);
    	System.out.println(lastId);*/
    	
    	//for(int i = 1; i <= 10; i++) {
    		System.out.println(dynamoWriter.getLastId(CNV_DYNAMODB_METRICS_TABLE));
    	//}
    	
    }
    
}
