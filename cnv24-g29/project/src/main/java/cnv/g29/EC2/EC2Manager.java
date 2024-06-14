package cnv.g29.EC2;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;
import software.amazon.awssdk.services.ec2.waiters.Ec2Waiter;

import static cnv.g29.AWSConstants.*;

import java.util.Arrays;
import java.util.List;

import cnv.g29.AWSConstants;


public class EC2Manager {

    public static Ec2Client createEc2Client(Region awsRegion, AwsCredentialsProvider credentials_provider) {
    	
        return Ec2Client.builder()
                .region(awsRegion)
                .credentialsProvider(credentials_provider)
                .build();
    }
    
    
    public static SecurityGroup createSecurityGroup(Ec2Client ec2) {
    	
    	DescribeSecurityGroupsResponse response = null;
        try {
            response = ec2.describeSecurityGroups(
                    DescribeSecurityGroupsRequest.builder()
                            .filters(
                                    Filter.builder()
                                            .name("group-name")
                                            .values(SECURITY_GROUP_NAME)
                                            .build()
                            )
                            .build()
            );
            if (response.securityGroups().isEmpty()) {
            	System.out.println("...Security group '" + SECURITY_GROUP_NAME + "' does not exist. Going to create. ");

            	String groupName = SECURITY_GROUP_NAME;
            	String groupDescription = "custom security group";

            	// Creates the security group
            	CreateSecurityGroupRequest createRequest = CreateSecurityGroupRequest.builder()
            			.groupName(groupName)
            			.description(groupDescription)
            			.vpcId(AWSConstants.VPC_ID)
            			.build();

            	CreateSecurityGroupResponse createResponse = ec2.createSecurityGroup(createRequest);
            	String groupId = createResponse.groupId();
            	System.out.println("...Security Group created: " + groupId);

            	// Define rules
            	List<IpPermission> ipPermissions = Arrays.asList(
            			IpPermission.builder()
            			.ipProtocol("tcp")
            			.fromPort(80)
            			.toPort(80)
            			.ipRanges(IpRange.builder().cidrIp("0.0.0.0/0").build())
            			.build(),
            			IpPermission.builder()
            			.ipProtocol("tcp")
            			.fromPort(8000)
            			.toPort(8000)
            			.ipRanges(IpRange.builder().cidrIp("0.0.0.0/0").build())
            			.build(),
            			IpPermission.builder()
            			.ipProtocol("tcp")
            			.fromPort(443)
            			.toPort(443)
            			.ipRanges(IpRange.builder().cidrIp("0.0.0.0/0").build())
            			.build(),
            			IpPermission.builder()
            			.ipProtocol("tcp")
            			.fromPort(22)
            			.toPort(22)
            			.ipRanges(IpRange.builder().cidrIp("0.0.0.0/0").build())
            			.build()
            			);

            	// Authorize the rules
            	AuthorizeSecurityGroupIngressRequest authRequest = AuthorizeSecurityGroupIngressRequest.builder()
            			.groupId(groupId)
            			.ipPermissions(ipPermissions)
            			.build();

            	AuthorizeSecurityGroupIngressResponse authResponse = ec2.authorizeSecurityGroupIngress(authRequest);
            	System.out.println(authResponse);
            	System.out.println("...Ingress rules added to security group: " + groupId);

            	DescribeSecurityGroupsResponse describeResponse = ec2.describeSecurityGroups(
            			DescribeSecurityGroupsRequest.builder()
            			.groupIds(groupId)
            			.build());
            	return describeResponse.securityGroups().get(0);

            } else {
            	System.out.println("...Security group " + SECURITY_GROUP_NAME + " exists.");
            }
        } catch (Ec2Exception e) {
        	System.out.println("Exception");
        }
        return response.securityGroups().get(0);
    }


    

    public static String runEC2Instance(Ec2Client ec2, String name, SecurityGroup security_group) {
    	
        //Instance
        RunInstancesRequest runRequest = RunInstancesRequest.builder()
                .imageId(VM_WORKER_AMI_ID)
                .instanceType(DEFAULT_INSTANCE_TYPE)
                .maxCount(1)
                .minCount(1)
                .securityGroupIds(security_group.groupId())
                .build();

        // Use a waiter to wait until the instance is running.
        System.out.println("...Going to start an EC2 instance using a waiter");
        RunInstancesResponse response = ec2.runInstances(runRequest);
        String instanceIdVal = response.instances().get(0).instanceId();
        ec2.waiter().waitUntilInstanceRunning(r -> r.instanceIds(instanceIdVal));
        Tag tag = Tag.builder()
                .key("Name")
                .value(name)
                .build();

        CreateTagsRequest tagRequest = CreateTagsRequest.builder()
                .resources(instanceIdVal)
                .tags(tag)
                .build();

        try {
            ec2.createTags(tagRequest);
            System.out.printf("...Successfully started EC2 Instance %s based on AMI %s\n", instanceIdVal, VM_WORKER_AMI_ID);
            return instanceIdVal;

        } catch (Ec2Exception e) {
            System.err.println(e.awsErrorDetails().errorMessage());
            System.exit(1);
        }

        return "";
    }

    public static void stopInstance(Ec2Client ec2, String instanceId) {
        WaiterResponse<DescribeInstancesResponse> waiterResponse;
        try (Ec2Waiter ec2Waiter = Ec2Waiter.builder()
                .overrideConfiguration(b -> b.maxAttempts(100))
                .client(ec2)
                .build()) {
            StopInstancesRequest request = StopInstancesRequest.builder()
                    .instanceIds(instanceId)
                    .build();

            System.out.println("...Use an Ec2Waiter to wait for the instance to stop. This will take a few minutes.");
            ec2.stopInstances(request);
            DescribeInstancesRequest instanceRequest = DescribeInstancesRequest.builder()
                    .instanceIds(instanceId)
                    .build();

            waiterResponse = ec2Waiter.waitUntilInstanceStopped(instanceRequest);
        }
        //waiterResponse.matched().response().ifPresent(System.out::println);
        System.out.println("...Successfully stopped instance " + instanceId);
    }

    public static void terminateInstance(Ec2Client ec2, String instanceId) {
        WaiterResponse<DescribeInstancesResponse> waiterResponse;
        try (Ec2Waiter ec2Waiter = Ec2Waiter.builder()
                .overrideConfiguration(b -> b.maxAttempts(100))
                .client(ec2)
                .build()) {
            TerminateInstancesRequest request = TerminateInstancesRequest.builder()
                    .instanceIds(instanceId)
                    .build();

            System.out.println("...Use an Ec2Waiter to wait for the instance to terminate. This will take a few minutes.");
            ec2.terminateInstances(request);
            DescribeInstancesRequest instanceRequest = DescribeInstancesRequest.builder()
                    .instanceIds(instanceId)
                    .build();

            waiterResponse = ec2Waiter.waitUntilInstanceTerminated(instanceRequest);
        }
        //waiterResponse.matched().response().ifPresent(System.out::println);
        System.out.println("...Successfully terminated instance " + instanceId);
    }

}
