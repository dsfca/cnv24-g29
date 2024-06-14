package cnv.g29.test;

import software.amazon.awssdk.services.ec2.Ec2Client;

import static cnv.g29.AWSConstants.AWS_DEFAULT_REGION;
import static cnv.g29.AWSConstants.DEFAULT_CREDENTIALS_PROVIDER;

import cnv.g29.EC2.EC2Manager;
import cnv.g29.LoadBalancerAndAutoscaler.LBManager;



public class TestLoadBalancer {
	
	private static Ec2Client client;
    
    
    public TestLoadBalancer() {
    	TestLoadBalancer.client = EC2Manager.createEc2Client(AWS_DEFAULT_REGION, DEFAULT_CREDENTIALS_PROVIDER);
    }
    
    
    public void testSystem() throws InterruptedException {
    	
    	String loadBalancer = LBManager.runLBInstance(client, LBManager.createSecurityGroup(client));
    	
    	Thread.sleep(1000 * 60 * 30);
    	
    	/** 
    	 * If we want to terminate all the instances in aws in a safe and automated way
    	 * LBManager.terminateSystem(client); */
    }
    
    
    public static void main(String[] args) throws InterruptedException {
    	TestLoadBalancer lb = new TestLoadBalancer();
        lb.testSystem();
    }

    
    // Checks the health of the instances in the target group.
//    public List<TargetHealthDescription> checkTargetHealth(String targetGroupName) {
//        DescribeTargetGroupsRequest targetGroupsRequest = DescribeTargetGroupsRequest.builder()
//                .names(targetGroupName)
//                .build();
//
//        DescribeTargetGroupsResponse tgResponse = getLoadBalancerClient().describeTargetGroups(targetGroupsRequest);
//
//        DescribeTargetHealthRequest healthRequest = DescribeTargetHealthRequest.builder()
//                .targetGroupArn(tgResponse.targetGroups().get(0).targetGroupArn())
//                .build();
//
//        DescribeTargetHealthResponse healthResponse = getLoadBalancerClient().describeTargetHealth(healthRequest);
//        return healthResponse.targetHealthDescriptions();
//    }



}
