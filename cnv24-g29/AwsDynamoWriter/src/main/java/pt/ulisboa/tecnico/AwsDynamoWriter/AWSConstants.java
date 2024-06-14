package pt.ulisboa.tecnico.AwsDynamoWriter;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;


public class AWSConstants {

    private AWSConstants() {
    }
    
    public static final AwsCredentialsProvider DEFAULT_CREDENTIALS_PROVIDER = EnvironmentVariableCredentialsProvider.create();
    public static final String AWS_DEFAULT_REGION_STR = "us-east-1";
    public static final Region AWS_DEFAULT_REGION = Region.US_EAST_1;
    public static String AMI_ID = "ami-0200745fcba33210e";


}
