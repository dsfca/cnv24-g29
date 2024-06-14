package cnv.g29.LoadBalancerAndAutoscaler;

import cnv.g29.lambda.LambdaManager;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import software.amazon.awssdk.services.ec2.model.Instance;
import software.amazon.awssdk.services.ec2.model.Tag;

import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import static cnv.g29.AWSConstants.EC2_PORT;
import static cnv.g29.AWSConstants.LOAD_BALANCER_NAME;
import static cnv.g29.AlgorithmParameters.*;


// java -cp target/webserver-1.0.0-SNAPSHOT-jar-with-dependencies.jar pt.ulisboa.tecnico.cnv.webserver.WebServer
public class LoadBalancerHandler implements HttpHandler {

    private final static ObjectMapper mapper = new ObjectMapper();
    private LoadBalancer loadBalancer;
    private final List<Instance> healthyInstances = new ArrayList<>();
    private final PriorityQueue<RequestInstanceDescriptor> requestInstanceDescriptors = new PriorityQueue<>();
    private final PriorityQueue<RequestInstanceDescriptor> initializingInstancesQueue = new PriorityQueue<>();

    private Map<Instance, InstanceTrackRecord> instanceTrackRecord = new HashMap<>();
    private final Map<String, Integer> requestTypes = Map.of(
            "/raytracer", 0,
            "/blurimage", 1,
            "/enhanceimage", 2,
            "/healthcheck", 3
    );
    private final Map<Integer, Integer> estimatedCostPerInstanceType = Map.of(
            0, TYPE0_STARTING_ESTIMATION,
            1, TYPE1_STARTING_ESTIMATION,
            2, TYPE2_STARTING_ESTIMATION,
            3, TYPE3_STARTING_ESTIMATION
    );
    
    private boolean isEC2Launched = false;


    public LoadBalancerHandler() {
    	loadBalancer = new LoadBalancer(this);
        this.updateHealthyInstances();
        this.healthyInstances.forEach(hi -> this.requestInstanceDescriptors.offer(new RequestInstanceDescriptor(hi)));
        initializeInstanceTrackRecord(loadBalancer.getRunningInstances());
        loadBalancer.getInitializingInstances().forEach(ii -> this.initializingInstancesQueue.offer(new RequestInstanceDescriptor(ii)));
    }
    
    public PriorityQueue<RequestInstanceDescriptor> getRequestInstanceDescriptors () {
    	return this.requestInstanceDescriptors;
    }
    
    public synchronized void updateRequestInstanceDescriptors(List <Instance> runningInstances) {
    	boolean contains = false;
    	for (Instance i: runningInstances) {
    		for (RequestInstanceDescriptor r: requestInstanceDescriptors) {
    			if (r.getInstance().equals(i)) {
    				contains = true;
    				break;
    			}
    		}
    		if (contains != true) {
    			requestInstanceDescriptors.add(new RequestInstanceDescriptor(i));
    		}
    		contains = false;
    	}
    }
    
    public synchronized void updateInitializingInstancesQueue(List <Instance> initializingInstances) {
    	boolean contains = false;
    	for (Instance i: initializingInstances) {
    		for (RequestInstanceDescriptor r: initializingInstancesQueue) {
    			if (r.getInstance().equals(i)) {
    				contains = true;
    				break;
    			}
    		}
    		if (contains != true) {
    			initializingInstancesQueue.add(new RequestInstanceDescriptor(i));
    		}
    		contains = false;
    	}
    }

    public void removeInstanceFromQueue(Instance i) {
        for (RequestInstanceDescriptor r : initializingInstancesQueue) {
            if (r.getInstance().equals(i)) {
                initializingInstancesQueue.remove(r);
            }
        }
    }
    
    public void removeInstanceFromRequestInstanceDescriptors(Instance i) {
        for (RequestInstanceDescriptor r : requestInstanceDescriptors) {
            if (r.getInstance().equals(i)) {
            	requestInstanceDescriptors.remove(r);
            }
        }
    }

    public void initializeInstanceTrackRecord(List<Instance> runningInstances) {
        for (Instance i : runningInstances) {
            instanceTrackRecord.put(i, new InstanceTrackRecord());
        }
    }

    public void updateInstanceTrackRecord(List<Instance> runningInstances) {
        for (Instance i : runningInstances) {
            if (!instanceTrackRecord.containsKey(i)) {
                instanceTrackRecord.put(i, new InstanceTrackRecord());
            }
        }
    }

    public void removeInstanceTrackRecord(Instance instance) {
        instanceTrackRecord.remove(instance);
    }
    
    public synchronized String receiveRequest(HttpExchange exchange) {
    	InputStream stream = exchange.getRequestBody();
        String requestBody = new BufferedReader(new InputStreamReader(stream)).lines().collect(Collectors.joining("\n"));
        return requestBody;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
    	
    	String requestBody = receiveRequest(exchange); //Json body
    	String response = "";

        //to do: add autoscaler functionality here (that its supposed to be called every "AUTOSCALER_CHECK_PERIODICITY" time): that checks if there are VMs without requests for time "VM_WORKER_MAX_TIME_WITHOUT_REQUESTS" and shuts them down
        //Dani: Lambdas shut down after a while without utilization right? we should keep operational lambdas in a list. After VM is up and lambda finished that last request -> shut down
        if (exchange.getRequestURI().toString().equals(HEALTH_CHECK_PATH) && this.healthyInstances.isEmpty())
            response = "No healthy instances found";
        else {
        	Map<String, Integer> ImageSize = new HashMap<>();
            Map<String, Object> bodyForRaytracer = null; //formatted body
            if ("/raytracer".equals(exchange.getRequestURI().getPath()))
                bodyForRaytracer = mapper.readValue(requestBody, new TypeReference<>() {
                });
            try {
            	URI requestUri = exchange.getRequestURI();
                String uriPath = requestUri.getPath();
                //Integer requestType = getRequestType(uriPath);
                Map<String, String> parameters = getParameters(uriPath, requestUri.getRawQuery(), bodyForRaytracer, requestBody);
                Instance targetInstance = forwardToInstanceOrLambda(exchange, bodyForRaytracer, requestBody, parameters);
                
                if(targetInstance != null) {
                	response = this.forwardRequestToInstance(exchange.getRequestMethod(), requestBody, requestUri.toString(), targetInstance.publicIpAddress());
                } else {
                	System.out.println("LB - forwarding to Lambda function");
                    response = LambdaManager.invokeFunction(uriPath, parameters);
                }
                //response = forwardToInstanceOrLambda(exchange, bodyForRaytracer, requestBody);
            } catch (InterruptedException e) {
            	System.out.println("RESEND REQUESTTTTTTT");
                throw new RuntimeException(e);         
            }

//            Instance targetInstance = getInstanceToRunMyTask(estimatedRequestCpuTime, exchange);
//            if (targetInstance != null) {
//                System.out.println("Forwarding request to " + targetInstance.publicIpAddress());
//                response = this.forwardRequestToInstance(exchange.getRequestMethod(), requestBody, requestURI, targetInstance.publicIpAddress());
//            } else {
//                if (!loadBalancer.isEC2Launched()) {
//                    loadBalancer.autoscaler(1);
//                } else {
//                    Instant EC2SpinTermination = Instant.now().plusMillis((long) (loadBalancer.EC2SpinTermination() + (loadBalancer.EC2SpinTermination() * MAX_WAITING_REQUEST_RATIO)));
//                    Instant estimatedRequestProcess = Instant.now().plusMillis((long) (estimatedRequestCpuTime));
//                    while (loadBalancer.isEC2Launched() && estimatedRequestProcess.isAfter(EC2SpinTermination)) {
//                        try {
//                            System.out.println("waiting for EC2 instance to spin");
//                            wait();
//                            EC2SpinTermination = Instant.now().plusMillis((long) (loadBalancer.EC2SpinTermination()));
//                            estimatedRequestProcess = Instant.now().plusMillis((long) (estimatedRequestCpuTime));
//                        } catch (InterruptedException e) {
//                            e.printStackTrace();
//                        }
//                    }
//                }
//
//                targetInstance = getInstanceToRunMyTask(estimatedRequestCpuTime, exchange);
//                if (targetInstance != null)
//                    response = this.forwardRequestToInstance(exchange.getRequestMethod(), requestBody, requestURI, targetInstance.publicIpAddress());
//                else {
//                    if (areInitializingInstancesCapable(estimatedRequestCpuTime, exchange) != null) {
//                        System.out.println("Initializing instance is capable.");
//                    } else {
//                        loadBalancer.autoscaler(1);
//                    }
//
//                    Map<String, String> parameters = getParameters(uriPath, requestUri.getRawQuery(), bodyForRaytracer, requestBody);
//                    response = LambdaManager.invokeFunction(uriPath, parameters);
//                }
//            }
        }
        System.out.println("GOT HERE");
        byte[] responseBytes = response.getBytes();
        sendResponse(responseBytes, exchange);
    }
    
    private synchronized Instance forwardToInstanceOrLambda(HttpExchange request, Map<String, Object> formattedBody, String jsonBody, Map<String, String> reqParams) throws InterruptedException, JsonProcessingException, IOException {
    	//this.updateHealthyInstances();
    	long estimatedRequestCpuTime = estimateCostForRequest(request.getRequestURI().getPath(), reqParams);
        System.out.println(estimatedRequestCpuTime);
        //Integer requestType = getRequestType(uriPath);

        //INSTANCE
        Instance targetInstance = getInstanceToRunMyTask(estimatedRequestCpuTime, request);
        if (targetInstance != null) {
        	return targetInstance;
            
        } else {
        	
            if (Objects.isNull(areInitializingInstancesCapable(estimatedRequestCpuTime)) /*&& !isEC2Launched*/) {
            	System.out.println("THEY ARE NOT CAPABLE");
                loadBalancer.autoscaleUsingThread(1);
                isEC2Launched = true;
            }
        }
        return null;
    }
    
    private String forwardRequestToInstance(String requestMethod, String requestBody, String requestURI, String publicIPAddress) {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = null;
        if (requestMethod.equals("GET")) {
            request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + publicIPAddress + ":" + EC2_PORT + requestURI))
                    .GET()
                    .build();
        } else if (requestMethod.equals("POST")) {
            request = HttpRequest.newBuilder()
                    .uri(URI.create("http://" + publicIPAddress + ":" + EC2_PORT + requestURI))
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
        }
        try {
            String response = client.send(request, HttpResponse.BodyHandlers.ofString()).body();
            synchronized (this) {
                this.notifyAll();
            }
            return /*"[" + publicIPAddress + "] - " + */response;
        } catch (InterruptedException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, String> getParameters(String path, String query, Map<String, Object> bodyForRaytracer, String bodyForImageProcessing) throws JsonParseException, JsonMappingException, IOException {
        Map<String, String> requestParameters;
        if (path.equals("/raytracer")) {
            requestParameters = getRaytracerParameters(query, bodyForRaytracer);
        } else {
            requestParameters = getImageProcessingParameters(bodyForImageProcessing);
        }

        return requestParameters;
    }

    private Map<String, String> getRaytracerParameters(String query, Map<String, Object> body) throws JsonParseException, JsonMappingException, IOException {
        Map<String, String> parameters = queryToMap(query);
        byte[] input = ((String) body.get("scene")).getBytes();
        byte[] texmap = null;

        if (body.containsKey("texmap")) {
            ArrayList<Integer> texmapBytes = (ArrayList<Integer>) body.get("texmap");
            texmap = new byte[texmapBytes.size()];
            for (int i = 0; i < texmapBytes.size(); i++) {
                texmap[i] = texmapBytes.get(i).byteValue();
            }
        }

        String inputBase64 = Base64.getEncoder().encodeToString(input);
        String texmapBase64 = Base64.getEncoder().encodeToString(texmap);
        parameters.put("input", inputBase64);
        parameters.put("texmap", texmapBase64);
        return parameters;
    }

    private Map<String, String> getImageProcessingParameters(String result) throws IOException {
        Map<String, String> requestParameters = new HashMap<String, String>();
        //InputStream stream = exchange.getRequestBody();
        //String result = new BufferedReader(new InputStreamReader(stream)).lines().collect(Collectors.joining("\n"));
        String[] resultSplits = result.split(",");
        String format = resultSplits[0].split("/")[1].split(";")[0];
        String body = resultSplits[1];
        requestParameters.put("body", body);
        requestParameters.put("fileFormat", format);
        
        byte[] decoded = Base64.getDecoder().decode(body);
    	ByteArrayInputStream bais = new ByteArrayInputStream(decoded);
        BufferedImage bi = ImageIO.read(bais);
        requestParameters.put("width", String.valueOf(bi.getWidth()));
        requestParameters.put("height", String.valueOf(bi.getHeight()));
        return requestParameters;
    }

    private Map<String, String> queryToMap(String query) {
        if (query == null) {
            return null;
        }
        Map<String, String> result = new HashMap<>();
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            } else {
                result.put(entry[0], "");
            }
        }
        return result;
    }

    public void sendResponse(byte[] responseBytes, HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(200, responseBytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(responseBytes);
        os.close();
        cleanAfterRequest(exchange);
    }

    private synchronized void cleanAfterRequest(HttpExchange exchange) {
        int index = 0;
        int target = 0;
        for (RequestInstanceDescriptor r : requestInstanceDescriptors) {
            for (HttpExchange he : r.getRequests()) {
                if (he.equals(exchange)) {
                    if (r.getRequests().size() == 1) {
                        r.setTotalCpuTime(Instant.now());
                    } else {
                        r.setTotalCpuTime(r.getTotalCpuTime().minusMillis((long) r.getRequestsDuration().get(index)));
                    }
                    target = index;
                }
                index++;
            }
            r.removeRequest(exchange);
            r.removeRequestDuration(index);
        }
    }

    /*private synchronized String forwardToInstanceOrLambda(HttpExchange request, Map<String, Object> formattedBody, String jsonBody) throws InterruptedException, JsonProcessingException, IOException {
        URI requestUri = request.getRequestURI();
        String uriPath = requestUri.getPath();
        //Integer requestType = getRequestType(uriPath);
        long estimatedRequestCpuTime = estimateCostForRequest(uriPath);
        System.out.println(estimatedRequestCpuTime);
        String response = "";

        //INSTANCE
        Instance targetInstance = getInstanceToRunMyTask(estimatedRequestCpuTime, request);
        if (targetInstance != null) {
            System.out.println("LB - Forwarding request to " + targetInstance.publicIpAddress());
            String requestBody = Objects.nonNull(formattedBody) ? mapper.writeValueAsString(formattedBody) : jsonBody;
            response = this.forwardRequestToInstance(request.getRequestMethod(), requestBody, requestUri.toString(), targetInstance.publicIpAddress());
            isEC2Launched = false;
            //LAMBDA
        } else {
        	System.out.println("LB - forwarding to Lambda function");
            Map<String, String> parameters = getParameters(uriPath, requestUri.getRawQuery(), formattedBody, jsonBody);
            if (Objects.isNull(areInitializingInstancesCapable(estimatedRequestCpuTime, request)) /*&& !isEC2Launched) {
                //System.out.println("Autoscaling: starting another EC2 instance in a different thread");
                loadBalancer.autoscaleUsingThread(1);
                isEC2Launched = true;
            }
            response = LambdaManager.invokeFunction(uriPath, parameters);
        }
        return response;
    }*/
    
    private long estimateCostForRequest(String uriPath, Map<String, String> requestParameters) {
        long estimatedCpuTime = 0;

        if (uriPath.equals("/blurimage") || uriPath.equals("/enhanceimage")) {
            int lines = Integer.valueOf(requestParameters.get("height"));
            int columns = Integer.valueOf(requestParameters.get("width"));

            if (uriPath.equals("/blurimage"))
                estimatedCpuTime = (long) (0.0004359 * (lines * columns) + 5.9923);
            else if (uriPath.equals("/enhanceimage"))
                estimatedCpuTime = (long) (0.0004581 * (lines * columns) - 22.70044);

        } else if (uriPath.equals("/raytracer")) {
        	estimatedCpuTime = 1000 * 60;
        	//estimatedCpuTime = (long) (-21.63 + 203.60 + 332.75 + 46.8);
        }
        System.out.println("--------------------Estimated CPU time for this request:" + estimatedCpuTime + "-----------------------------");
        return estimatedCpuTime;
    }
    

    private synchronized Instance getInstanceToRunMyTask(long estimatedRequestCpuTime, HttpExchange request) {
    	loadBalancer.updateRunningInstances();
        RequestInstanceDescriptor targetRequestInstanceDescriptor = this.requestInstanceDescriptors.peek();
        if (Objects.isNull(targetRequestInstanceDescriptor)) // TODO: Should i spin an instance or fire a lambda? Dani: Both, lambda works while instance is starting (given that we do not have any functional instance, we need to spin one)
            return null;

        //Calculate cpu time for instance
        Instant targetInstanceCpuTime = targetRequestInstanceDescriptor.getTotalCpuTime();
        Instant maximumRequestWaitingTime = Instant.now().plusMillis((long) (estimatedRequestCpuTime * MAX_WAITING_REQUEST_RATIO));

        if (targetInstanceCpuTime.isBefore(maximumRequestWaitingTime)) {
        	System.out.println("METRICS - Target instance current cpu time: " + targetInstanceCpuTime);
        	if (targetRequestInstanceDescriptor.getRequests().size() == 0) {
        		targetRequestInstanceDescriptor.setTotalCpuTime(Instant.now().plusMillis(estimatedRequestCpuTime));
        	} else {
        		targetRequestInstanceDescriptor.setTotalCpuTime(targetInstanceCpuTime.plusMillis(estimatedRequestCpuTime));
        	}
            System.out.println("METRICS - Target instance new cpu time: " + targetRequestInstanceDescriptor.getTotalCpuTime());
            targetRequestInstanceDescriptor.addRequest(request);
            return targetRequestInstanceDescriptor.getInstance();
        }
        return null;
    }

    private Instance areInitializingInstancesCapable(long estimatedRequestCpuTime) {
        loadBalancer.updateInitializingInstances();
        //System.out.println("Info: initializing instances:" + this.initializingInstancesQueue.toString());
        RequestInstanceDescriptor targetRequestInstanceDescriptor = this.initializingInstancesQueue.peek();
        if (Objects.isNull(targetRequestInstanceDescriptor)) {
        	return null;
        }

        //Calculate cpu time for instance
        Instant targetInstanceCpuTime = targetRequestInstanceDescriptor.getTotalCpuTime();
        Instant maximumRequestWaitingTime = Instant.now().plusMillis((long) (Math.max(estimatedRequestCpuTime * MAX_WAITING_REQUEST_RATIO, ACCEPTABLE_REQUEST_WAIT)));
        if (targetInstanceCpuTime.isBefore(maximumRequestWaitingTime)) {
            System.out.println("METRICS Check: Initializing instance current cpu time: " + targetInstanceCpuTime);
            if (targetRequestInstanceDescriptor.getRequests().size() == 0) {
        		targetRequestInstanceDescriptor.setTotalCpuTime(Instant.now().plusMillis(estimatedRequestCpuTime));
        	} else {
        		targetRequestInstanceDescriptor.setTotalCpuTime(targetInstanceCpuTime.plusMillis(estimatedRequestCpuTime));
        	}
            System.out.println("METRICS Check: Initializing instance new cpu time: " + targetRequestInstanceDescriptor.getTotalCpuTime());
            //targetRequestInstanceDescriptor.addRequest(request);
            return targetRequestInstanceDescriptor.getInstance();
        }
        return null;
    }


    private Integer getRequestType(String requestURI) {
        return this.requestTypes.get(requestURI);
    }

    public synchronized void updateHealthyInstances() {
        System.out.println("LB - Updating healthy instances.");

        loadBalancer.getRunningInstancesPublicIPs(); //this updates running instances NICAS it shouldnt
        updateInstanceTrackRecord(loadBalancer.getRunningInstances());

        this.loadBalancer.getRunningInstances().forEach(instance -> {

            if (!getInstanceName(instance).equals(LOAD_BALANCER_NAME)) {

                if (this.sendHealthCheck(instance.publicIpAddress())) {

                    if (!healthyInstances.contains(instance)) {
                        this.healthyInstances.add(instance);
                        instanceTrackRecord.get(instance).setSequentialFails(0);
                    }
                    instanceTrackRecord.get(instance).incrementSuccessfulHealthChecks();

                    // if healthcheck not ok
                } else {
                    this.healthyInstances.remove(instance);
                    instanceTrackRecord.get(instance).incrementSequentialFails();
                    instanceTrackRecord.get(instance).incrementFailsAcrossTime();

                }
            }
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


    private boolean sendHealthCheck(String publicIPAddress) {
        if (Objects.isNull(publicIPAddress))
            return false;
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + publicIPAddress + ":" + EC2_PORT + HEALTH_CHECK_PATH))
                .GET()
                .build();
        try {
            int responseCode = client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode();
            return responseCode == 200;
        } catch (IOException | InterruptedException e) {
            System.out.println("Exeption occurred: " + e);
            throw new RuntimeException(e);
        }
    }

    

    

    /**
     * IntanceTrackRecord
     * <p>
     * Functions
     */

/*    private synchronized int getInstanceIndex(Instance instance) {
        for (int i = 0; i < instanceTrackRecord.size(); i++) {
            InstanceTrackRecord record = instanceTrackRecord.get(i);
            if (record.getInstance().equals(instance)) {
                return i;
            }
        }
        return -1;
    }*/

}