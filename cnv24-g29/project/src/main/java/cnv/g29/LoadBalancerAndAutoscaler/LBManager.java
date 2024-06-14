package cnv.g29.LoadBalancerAndAutoscaler;

import java.util.Arrays;
import java.util.List;

import cnv.g29.AWSConstants;
import cnv.g29.EC2.EC2Manager;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.AuthorizeSecurityGroupIngressRequest;
import software.amazon.awssdk.services.ec2.model.AuthorizeSecurityGroupIngressResponse;
import software.amazon.awssdk.services.ec2.model.CreateSecurityGroupRequest;
import software.amazon.awssdk.services.ec2.model.CreateSecurityGroupResponse;
import software.amazon.awssdk.services.ec2.model.CreateTagsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeSecurityGroupsResponse;
import software.amazon.awssdk.services.ec2.model.Ec2Exception;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.IpPermission;
import software.amazon.awssdk.services.ec2.model.IpRange;
import software.amazon.awssdk.services.ec2.model.RunInstancesRequest;
import software.amazon.awssdk.services.ec2.model.RunInstancesResponse;
import software.amazon.awssdk.services.ec2.model.SecurityGroup;
import software.amazon.awssdk.services.ec2.model.StopInstancesRequest;
import software.amazon.awssdk.services.ec2.model.Tag;
import software.amazon.awssdk.services.ec2.model.TerminateInstancesRequest;
import software.amazon.awssdk.services.ec2.waiters.Ec2Waiter;

import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;


import static cnv.g29.AWSConstants.*;

public class LBManager {

	public LBManager() {
		// TODO Auto-generated constructor stub
	}

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
							.values(LB_SECURITY_GROUP)
							.build()
							)
					.build()
					);
			if (response.securityGroups().isEmpty()) {
				System.out.println("Security group '" + LB_SECURITY_GROUP + "' does not exist. Going to create. ");

				String groupName = LB_SECURITY_GROUP;
				String groupDescription = "custom security group";

				// Creates the security group
				CreateSecurityGroupRequest createRequest = CreateSecurityGroupRequest.builder()
						.groupName(groupName)
						.description(groupDescription)
						.vpcId(AWSConstants.VPC_ID)
						.build();

				CreateSecurityGroupResponse createResponse = ec2.createSecurityGroup(createRequest);
				String groupId = createResponse.groupId();
				System.out.println("Security Group created: " + groupId);

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
						.fromPort(LB_PORT)
						.toPort(LB_PORT)
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
				System.out.println("Ingress rules added to security group: " + groupId);

				DescribeSecurityGroupsResponse describeResponse = ec2.describeSecurityGroups(
						DescribeSecurityGroupsRequest.builder()
						.groupIds(groupId)
						.build());
				return describeResponse.securityGroups().get(0);

			} else {
				System.out.println("Security group " + LB_SECURITY_GROUP + " exists.");
			}
		} catch (Ec2Exception e) {
			System.out.println("Exception");
		}
		return response.securityGroups().get(0);
	}




	public static String runLBInstance(Ec2Client ec2, SecurityGroup security_group) {

		//Instance
		RunInstancesRequest runRequest = RunInstancesRequest.builder()
				.imageId(LOAD_BALANCER_AMI_ID)
				.instanceType(DEFAULT_INSTANCE_TYPE)
				.maxCount(1)
				.minCount(1)
				.securityGroupIds(security_group.groupId())
				.build();

		// Use a waiter to wait until the instance is running.
		System.out.println("Going to start an LB using a waiter");
		RunInstancesResponse response = ec2.runInstances(runRequest);
		String instanceIdVal = response.instances().get(0).instanceId();
		ec2.waiter().waitUntilInstanceRunning(r -> r.instanceIds(instanceIdVal));
		Tag tag = Tag.builder()
				.key("Name")
				.value(LOAD_BALANCER_NAME)
				.build();

		CreateTagsRequest tagRequest = CreateTagsRequest.builder()
				.resources(instanceIdVal)
				.tags(tag)
				.build();

		try {
			ec2.createTags(tagRequest);
			System.out.printf("Successfully started LB %s based on AMI %s\n", instanceIdVal, LOAD_BALANCER_AMI_ID);
			return instanceIdVal;

		} catch (Ec2Exception e) {
			System.err.println(e.awsErrorDetails().errorMessage());
			System.exit(1);
		}

		return "";
	}

	public static void stopLBInstance(Ec2Client ec2, String instanceId) {
		WaiterResponse<DescribeInstancesResponse> waiterResponse;
		try (Ec2Waiter ec2Waiter = Ec2Waiter.builder()
				.overrideConfiguration(b -> b.maxAttempts(100))
				.client(ec2)
				.build()) {
			StopInstancesRequest request = StopInstancesRequest.builder()
					.instanceIds(instanceId)
					.build();

			System.out.println("Use an Ec2Waiter to wait for the LB to stop. This will take a few minutes.");
			ec2.stopInstances(request);
			DescribeInstancesRequest instanceRequest = DescribeInstancesRequest.builder()
					.instanceIds(instanceId)
					.build();

			waiterResponse = ec2Waiter.waitUntilInstanceStopped(instanceRequest);
		}
		waiterResponse.matched().response().ifPresent(System.out::println);
		System.out.println("Successfully stopped instance " + instanceId);
	}

	public static void terminateLBInstance(Ec2Client ec2, String instanceId) {
		WaiterResponse<DescribeInstancesResponse> waiterResponse;
		try (Ec2Waiter ec2Waiter = Ec2Waiter.builder()
				.overrideConfiguration(b -> b.maxAttempts(100))
				.client(ec2)
				.build()) {
			TerminateInstancesRequest request = TerminateInstancesRequest.builder()
					.instanceIds(instanceId)
					.build();

			System.out.println("Use an Ec2Waiter to wait for the LB to terminate. This will take a few minutes.");
			ec2.terminateInstances(request);
			DescribeInstancesRequest instanceRequest = DescribeInstancesRequest.builder()
					.instanceIds(instanceId)
					.build();

			waiterResponse = ec2Waiter.waitUntilInstanceTerminated(instanceRequest);
		}
		waiterResponse.matched().response().ifPresent(System.out::println);
		System.out.println("Successfully terminated LB " + instanceId);
	}
	

	//Shut down all the working instances and the Load Balancer
	public static void terminateSystem(Ec2Client client) {
		
		DescribeInstancesRequest request = DescribeInstancesRequest.builder().build();
		DescribeInstancesResponse response = client.describeInstances(request);
		
		response.reservations().forEach(reservation -> {
			reservation.instances().forEach(instance -> {
				
				/** STATUS ex: "Initializing" */
				DescribeInstanceStatusRequest statusRequest = DescribeInstanceStatusRequest.builder()
                        .instanceIds(instance.instanceId())
                        .build();

                DescribeInstanceStatusResponse statusResponse = client.describeInstanceStatus(statusRequest);
                
                if (!statusResponse.instanceStatuses().isEmpty()) {
                	String statusCheck = statusResponse.instanceStatuses().get(0).instanceStatus().status().toString();
                	System.out.println("Status: " + statusCheck);
                }
				/**/
                
				if (!instance.state().nameAsString().equals("terminated")) {
					if (!getInstanceName(instance).equals(LOAD_BALANCER_NAME)) {
						EC2Manager.stopInstance(client, instance.instanceId());
						EC2Manager.terminateInstance(client, instance.instanceId());
						System.out.println("Terminated Instance " + instance.instanceId());
					}
				}
			});
		});
		response.reservations().forEach(reservation -> {
			reservation.instances().forEach(instance -> {
				
				if (!instance.state().nameAsString().equals("terminated")) {
					if (getInstanceName(instance).equals(LOAD_BALANCER_NAME)) {
						stopLBInstance(client, instance.instanceId());
						terminateLBInstance(client, instance.instanceId());	
					}
				}
			});
		});
	}

	private static String getInstanceName(Instance instance_name) {
        for (Tag tag : instance_name.tags()) {
            if ("Name".equals(tag.key())) {
                return tag.value();
            }
        }
        return "No instance with that name"; 
    }
	

	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}

}
