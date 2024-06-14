package cnv.g29.test;

import static cnv.g29.AWSConstants.AWS_DEFAULT_REGION;
import static cnv.g29.AWSConstants.DEFAULT_CREDENTIALS_PROVIDER;

import cnv.g29.EC2.EC2Manager;
import cnv.g29.EC2.EC2MeasureCPU;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.Instance;


/**
 * Hello world!
 * first instance id = i-0861ed05cb83c5434
 */
public class TestEC2 {

    private static Ec2Client client;


    public TestEC2() {
        TestEC2.client = EC2Manager.createEc2Client(AWS_DEFAULT_REGION, DEFAULT_CREDENTIALS_PROVIDER);
    }


    public void testSystem() throws InterruptedException {
        EC2MeasureCPU cpu = new EC2MeasureCPU(DEFAULT_CREDENTIALS_PROVIDER, client);
        cpu.testCPUMetric();
        String firstEc2InstanceId = EC2Manager.runEC2Instance(client, "VMWorker", EC2Manager.createSecurityGroup(client));
//        String secondEc2InstanceId = EC2Manager.runEC2Instance(client, "withSecurityGroup_2", EC2Manager.createSecurityGroup(client));

        
        
        for(int x = 0; x < 10000; x++) {
            // Sleep 70s
        	Thread.sleep(1000 * 240);
        	cpu.testCPUMetric();
        	//Test CPU usage for specific instance
        	System.out.println("CPU for instance " + firstEc2InstanceId + ", CPU: " + cpu.getCPUUsageForInstance(firstEc2InstanceId));
        }
        
        /*EC2Manager.stopInstance(client, firstEc2InstanceId);
        EC2Manager.stopInstance(client, secondEc2InstanceId);
        EC2Manager.terminateInstance(client, firstEc2InstanceId);
        EC2Manager.terminateInstance(client, secondEc2InstanceId);*/

        client.close();
    }


    /*****REMOVE AFTER********
     *
     * RETURN INSTANCE GIVEN ID AS STRING
     */
    public Instance getInstanceById(Ec2Client ec2, String instanceId) {
        DescribeInstancesRequest request = DescribeInstancesRequest.builder()
                .filters(Filter.builder()
                        .name("instance-id")
                        .values(instanceId)
                        .build())
                .build();

        DescribeInstancesResponse response = ec2.describeInstances(request);

        if (!response.reservations().isEmpty()) {
            return response.reservations().get(0).instances().get(0);
        } else {
            System.out.println("Instance not found with ID: " + instanceId);
            return null;
        }
    }
    /*****END*********/


    public static void main(String[] args) throws InterruptedException {
        TestEC2 ec2 = new TestEC2();
        ec2.testSystem();
    }
    
    
}
