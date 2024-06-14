package cnv.g29;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.model.InstanceType;

public class AWSConstants {

    private AWSConstants() {
    }
    
    //EC2 Client
    public static final AwsCredentialsProvider DEFAULT_CREDENTIALS_PROVIDER = EnvironmentVariableCredentialsProvider.create();
    
    //AWS apply to all
    public static final InstanceType DEFAULT_INSTANCE_TYPE = InstanceType.T2_MICRO;
    public static final String AWS_DEFAULT_REGION_STR = "us-east-1";
    public static final Region AWS_DEFAULT_REGION = Region.US_EAST_1;
    public static final String SECURITY_GROUP_NAME = "launch-wizard-VM";
    public static final String VPC_ID = "vpc-047270013f11a3760";
    public static String DEFAULT_SECURITY_GROUP = "defaultSecurityGroup";
    
    //VM Worker
    public static String VM_WORKER_AMI_ID = "ami-005a252500dd78812";
    public static String VM_WORKER_NAME = "VM_WORKER";
    public static int EC2_PORT = 8000;
    
    //Load Balancer
    public static String LOAD_BALANCER_AMI_ID = "";
    public static String LOAD_BALANCER_NAME = "LoadBalancer-01";
    public static final int LB_PORT = 8080;
    public static String LB_SECURITY_GROUP = "LB-securityGroup-nw";
    
    

}
