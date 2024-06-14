package cnv.g29.LoadBalancerAndAutoscaler;

import cnv.g29.EC2.EC2Manager;
import com.amazonaws.AmazonClientException;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.*;

import java.awt.desktop.ScreenSleepEvent;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static cnv.g29.AWSConstants.*;
import static cnv.g29.AlgorithmParameters.*;
import static pt.ulisboa.tecnico.AwsDynamoWriter.AwsDynamoWriter.getlastNItemsStatic;


// Successfully started EC2 Instance i-047bbf34e6df38503 based on AMI ami-0200745fcba33210e
public class LoadBalancer {


    private List<Instance> runningInstances = new ArrayList<>();
    private List<Instance> initializingInstances = new ArrayList<>();

    private LoadBalancerHandler handler;

    //AWS
    private final Ec2Client ec2Client;
    private int lastIdReadFromDynamo = 0;
    private final String CNV_DYNAMODB_METRICS_TABLE = "CNV-WEBSERVER-METRICS";
    private final int EC2SpinTermination = 1000 * 60; // 2min to spin an instance
    private boolean EC2Launched = true;

    public LoadBalancer(LoadBalancerHandler handler) {
        this.handler = handler;
        this.ec2Client = EC2Manager.createEc2Client(AWS_DEFAULT_REGION, DEFAULT_CREDENTIALS_PROVIDER);
        updateInitializingInstances();
        updateRunningInstances();
        reduceEC2();
    }

    public boolean isEC2Launched() {
        return EC2Launched;
    }

    public int EC2SpinTermination() {
        return EC2SpinTermination;
    }

    public List<Instance> getRunningInstances() {
        return runningInstances;
    }

    public List<Instance> getInitializingInstances() {
        return initializingInstances;
    }

    public List<String> getRunningInstancesPublicIPs() {
        this.updateRunningInstances();
        return runningInstances.stream().map(Instance::publicIpAddress).filter(Objects::nonNull).toList();
    }

    public void updateRunningInstances() {
        runningInstances.clear();
        try {
            List<Reservation> reservations = ec2Client.describeInstances().reservations();
            this.runningInstances = reservations.stream().map(Reservation::instances).filter(Objects::nonNull)
                    .flatMap(Collection::stream).filter(instance -> instance.state().nameAsString().equalsIgnoreCase("running"))
                    .filter(instance -> {

                        DescribeInstanceStatusRequest statusRequest = DescribeInstanceStatusRequest.builder()
                                .instanceIds(instance.instanceId())
                                .build();
                        DescribeInstanceStatusResponse statusResponse = ec2Client.describeInstanceStatus(statusRequest);


                        InstanceStatus instanceStatus = statusResponse.instanceStatuses().stream()
                                .filter(status -> status.instanceId().equals(instance.instanceId()))
                                .findFirst()
                                .orElse(null);

                        InstanceStatusSummary instanceStatusSummary = instanceStatus.instanceStatus();
                        InstanceStatusSummary systemStatusSummary = instanceStatus.systemStatus();
                        return instanceStatusSummary.status().toString().equals("ok") && systemStatusSummary.status().toString().equals("ok");
                    })
                    .collect(Collectors.toList());
            //System.out.println(runningInstances);
            System.out.println("LB - Running instances: " + runningInstances.stream().map(Instance::publicIpAddress).toList());
        } catch (
                AmazonClientException e) {
            System.err.println("Error fetching EC2 instance info: " + e.getMessage());
        }
        handler.updateRequestInstanceDescriptors(this.runningInstances);
    }

    public void updateInitializingInstances() {
    	initializingInstances.clear();
        try {
            List<Reservation> reservations = ec2Client.describeInstances().reservations();

            List<String> instanceIds = reservations.stream()
                    .map(Reservation::instances)
                    .filter(Objects::nonNull)
                    .flatMap(List::stream)
                    .map(Instance::instanceId)
                    .collect(Collectors.toList());


            DescribeInstanceStatusRequest statusRequest = DescribeInstanceStatusRequest.builder()
                    .instanceIds(instanceIds)
                    .includeAllInstances(true)
                    .build();
            DescribeInstanceStatusResponse statusResponse = ec2Client.describeInstanceStatus(statusRequest);

            Set<String> initializingInstanceIds = statusResponse.instanceStatuses().stream()
                    .filter(status -> status.instanceStatus().statusAsString().equalsIgnoreCase("initializing"))
                    .map(InstanceStatus::instanceId)
                    .collect(Collectors.toSet());


            this.initializingInstances = reservations.stream().map(Reservation::instances).filter(Objects::nonNull)
                    .flatMap(Collection::stream).filter(instance -> instance.state().nameAsString().equalsIgnoreCase("Pending") ||
                            initializingInstanceIds.contains(instance.instanceId()))
                    .collect(Collectors.toList());
            System.out.println("Initializing Instances: " + initializingInstances.size() + ", IPs: " +  initializingInstances.stream().map(Instance::publicIpAddress).toList());
        } catch (
                AmazonClientException e) {
            System.err.println("Error fetching EC2 instance info: " + e.getMessage());
        }
        handler.updateInitializingInstancesQueue(this.initializingInstances);
    }


    public List<Map<String, AttributeValue>> getUpdatedMetricsFromDynamo() {
        List<Map<String, AttributeValue>> updatedMetrics = getlastNItemsStatic(CNV_DYNAMODB_METRICS_TABLE, lastIdReadFromDynamo);
        lastIdReadFromDynamo += updatedMetrics.size() + 1;
        return updatedMetrics;
    }


    public void updateLoadBalancerParameters() {
//        List<Map<String, AttributeValue>> updatedMetrics = getUpdatedMetricsFromDynamo(); // TODO: read read from dynamo
        // update metrics in dynamo.
    }


    public void autoscaleUsingThread(int operationId) {
        Thread thread = new Thread(new EC2Scale(operationId));
        thread.start();
    }
    
    
    public void reduceEC2() {
    	Thread thread = new Thread(new ReduceEC2());
    	thread.start();
    }

    private class EC2Scale implements Runnable {

        int operationId;

        public EC2Scale(int operationId) {
            this.operationId = operationId;
        }

        @Override
        public void run() {
            autoscaler(this.operationId);
        }

    }
    
    private class ReduceEC2 implements Runnable {


        @Override
        public void run() {
        	try {
        		while(true) {
					Thread.sleep(AUTOSCALER_CHECK_INSTANCES_WITHOUT_WORK_PERIODICITY);
					 autoscaler(0);
        		}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
        }

    }
    
//
//    private synchronized void scale() {
//    	Ec2Client client = EC2Manager.createEc2Client(AWS_DEFAULT_REGION, DEFAULT_CREDENTIALS_PROVIDER);
//
//    	EC2Manager.runEC2Instance(client, "VMWorker", EC2Manager.createSecurityGroup(client));
//    	/*try {
//    		int currentInstancesRunning = runningInstances.size();
//    		int sleepingTime = EC2SpinTermination;
//    		do {
//				Thread.sleep(sleepingTime);
//				updateRunningInstances();
//				sleepingTime = (int) (sleepingTime * MAX_WAITING_REQUEST_RATIO);
//    		}while(runningInstances.size() <= currentInstancesRunning );
//
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//    	System.out.println("new instance started");*/
//    	client.close();
//    	EC2Launched = false;
//    	handler.notifyAll();
//    }

    private void autoscaler(int operation_code) {

        if (operation_code == 1) {
        	System.out.println("AS - starting another EC2 instance");
            Ec2Client client = EC2Manager.createEc2Client(AWS_DEFAULT_REGION, DEFAULT_CREDENTIALS_PROVIDER);
            EC2Manager.runEC2Instance(client, VM_WORKER_NAME, EC2Manager.createSecurityGroup(client)); // TODO: add thread to avoid waiting for ec2's creation
            //Spin Lambda

        } else if (operation_code == 0) {
        	handler.updateHealthyInstances();
        	List<Instance> restingInstances = new ArrayList<Instance>();
            List<RequestInstanceDescriptor> requestsInstancesToRemove = new ArrayList<RequestInstanceDescriptor>();
            for (RequestInstanceDescriptor r : handler.getRequestInstanceDescriptors()) {
            	System.out.println("autoscaler " + r.getTotalCpuTime().plusMillis(VM_WORKER_MAX_TIME_WITHOUT_REQUESTS) + "    " + Instant.now() + "     " + r.getRequests().size());
                if (r.getTotalCpuTime().plusMillis(VM_WORKER_MAX_TIME_WITHOUT_REQUESTS).isBefore(Instant.now()) && r.getRequests().size() == 0) {
                	this.runningInstances.remove(r.getInstance());
                	System.out.println("AS - Inactive instance - Stoping instance: " + r.getInstance().publicIpAddress());
                    EC2Manager.stopInstance(ec2Client, r.getInstance().instanceId());
                    updateRunningInstances();
                    handler.removeInstanceFromRequestInstanceDescriptors(r.getInstance());
                    handler.removeInstanceFromQueue(r.getInstance());
                    handler.removeInstanceTrackRecord(r.getInstance());
                    EC2Manager.terminateInstance(ec2Client, r.getInstance().instanceId());
                }
            }
            /*For(Instances i: priority queue)
             *
             * 		If there are Instances not working && (LastRequestFinished > VM_WORKER_MAX_TIME_WITHOUT_REQUESTS)
             * 			stopInstance();
             * 			remove instance from trackRecord list*/
        } else if (operation_code == 2) {
            /*For(Instances i: running Instances)
             *
             * 		If i.sequentialFails >=  VM_WORKER_MAX_SEQUENTIAL_HEALTHCHECK_FAILS
             * 			|| i.successfulHealthChecks/i.failsAcrossTime >= VM_WORKER_MAX_FAILS_ACROSS_TIME_RATIO:
             * 				List<> requests = getRequestsThatInstanceHasOnItsList();
             * 				Access Threads responsible for requests in list above, and make them run handle again, so they can be reassign to another instance
             * 				i.stopInstance();
             * 				updateRunningInstances(); //needed here or redundant given our code next?
             * 				updateRunningHealthyInstances();
             * 				updateTrackRecord();
             * 				return requests;*/
        }
    }
}
