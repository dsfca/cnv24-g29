package cnv.g29.EC2;

import java.util.List;

import cnv.g29.AWSConstants;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;

import com.amazonaws.AmazonServiceException;

import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Datapoint;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsRequest;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsResponse;
import software.amazon.awssdk.services.cloudwatch.model.Statistic;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesRequest;
import software.amazon.awssdk.services.ec2.model.DescribeInstancesResponse;


public class EC2MeasureCPU {

    // Total observation time in milliseconds.
    private static long OBS_TIME = 1000 * 60 * 20;
    
    private AwsCredentialsProvider credentials_provider;
    private Ec2Client client;
    
    
    public EC2MeasureCPU (AwsCredentialsProvider credentials_provider, Ec2Client client) {
    	this.credentials_provider = credentials_provider;
    	this.client = client;
    }
    
    /**
     * Returns "null" if instance has no data yet
     */
    public String getCPUUsageForInstance(String instanceId) {
        
    	CloudWatchClient cloudWatchClient = CloudWatchClient.builder()
                .region(AWSConstants.AWS_DEFAULT_REGION)
                .credentialsProvider(credentials_provider)
                .build();

        try {
            Dimension instanceDimension = Dimension.builder()
                    .name("InstanceId")
                    .value(instanceId)
                    .build();

            GetMetricStatisticsRequest metricsRequest = GetMetricStatisticsRequest.builder()
                    .startTime(Instant.now().minusMillis(OBS_TIME))
                    .namespace("AWS/EC2")
                    .period(60)
                    .metricName("CPUUtilization")
                    .statistics(Statistic.AVERAGE)
                    .dimensions(instanceDimension)
                    .endTime(Instant.now())
                    .build();

            GetMetricStatisticsResponse metricsResponse = cloudWatchClient.getMetricStatistics(metricsRequest);

            List<Datapoint> datapoints = new ArrayList<>(metricsResponse.datapoints());
            datapoints.sort(Comparator.comparing(Datapoint::timestamp).reversed());

            if (!datapoints.isEmpty()) {
                return String.format("%.2f", datapoints.get(0).average()) + "%";
            } else {
                System.out.println("No CPU metric data available for instance: " + instanceId);
                return null;
            }

        } catch (AmazonServiceException ase) {
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Response Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());
            return null;
        } finally {
            cloudWatchClient.close();
        }
    }

    
    public void testCPUMetric() {
    	System.out.println("===========================================");
        System.out.println("EC2MeasureCPU:");
        System.out.println(" ");
        
        CloudWatchClient cloudWatchClient = CloudWatchClient.builder()
        		.region(AWSConstants.AWS_DEFAULT_REGION)
        		.credentialsProvider(credentials_provider)
                .build();
       
        try {
        	/*Set<Instance> instances = new HashSet<>();*/
        	
        	DescribeInstancesRequest request = DescribeInstancesRequest.builder().build();
        	DescribeInstancesResponse response = client.describeInstances(request);
              
       
        	response.reservations().forEach(reservation -> {
                reservation.instances().forEach(instance -> {
                	
                	//Not to print "terminated" instances
                	if (!instance.state().nameAsString().equals("terminated")) {
                        /*System.out.println("Instance ID: " + instance.instanceId() + " state: " + instance.state());*/
                		
                		// Defines the dimensions for the CloudWatch metrics request
                        Dimension instanceDimension = Dimension.builder()
                                .name("InstanceId")
                                .value(instance.instanceId())
                                .build();

                        // Creates the GetMetricStatisticsRequest
                        GetMetricStatisticsRequest metricsRequest = GetMetricStatisticsRequest.builder()
                                .startTime(Instant.now().minusMillis(OBS_TIME))
                                .namespace("AWS/EC2")
                                .period(60)
                                .metricName("CPUUtilization")
                                .statistics(Statistic.AVERAGE)
                                .dimensions(instanceDimension)
                                .endTime(Instant.now())
                                .build();
                        
                        // Calls CloudWatch to get metrics
                        GetMetricStatisticsResponse metricsResponse = cloudWatchClient.getMetricStatistics(metricsRequest);
                        
                        // Order datapoints by timestamp
                        List<Datapoint> datapoints = new ArrayList<>(metricsResponse.datapoints());
                        datapoints.sort(Comparator.comparing(Datapoint::timestamp));

                        // Processes the response
                        /*metricsResponse.datapoints().forEach(datapoint -> {
                            System.out.println("Timestamp: " + datapoint.timestamp() + ", Average CPU Utilization: " + datapoint.average());
                        });*/
                        
                        datapoints.forEach(datapoint -> {
                            System.out.println("InstanceID: " +  instance.instanceId() + ", Timestamp: " + datapoint.timestamp() + ", CPU: " + datapoint.average());
                        });
                    }
                    
                });
                    
            });
        	
        } catch (AmazonServiceException ase) {
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Reponse Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());
    
        }
        cloudWatchClient.close();
        
        System.out.println("===========================================");
    }

}

