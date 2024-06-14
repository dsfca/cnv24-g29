package cnv.g29;




public class AlgorithmParameters {

    private AlgorithmParameters() {
    }
    
    public static final int TYPE0_STARTING_ESTIMATION = 1000;
    public static final int TYPE1_STARTING_ESTIMATION = 500;
    public static final int TYPE2_STARTING_ESTIMATION = 2000;
    public static final int TYPE3_STARTING_ESTIMATION = 10;

    public static Integer VM_WORKER_MAX_SEQUENTIAL_HEALTHCHECK_FAILS = 10;
    public static Integer VM_WORKER_MAX_FAILS_ACROSS_TIME_RATIO = 5; // (successfulHealthChecks / failsAcrossTime) -> From class "InstanceTrackRecord"
    public static Double MAX_WAITING_REQUEST_RATIO = 0.35; //Request can wait 35% of its CPUtime
    public static Integer ACCEPTABLE_REQUEST_WAIT = 1000 * 2; //2s
    public static Integer AUTOSCALER_CHECK_PERIODICITY = 1000 * 30; //Needs to be time and not number of requests because receiving 10 requests of 1s each is not the same as receiving 10 requests of 1min
    public static Integer AUTOSCALER_CHECK_INSTANCES_WITHOUT_WORK_PERIODICITY = 1000 * 10;
    public static long VM_WORKER_MAX_TIME_WITHOUT_REQUESTS = 1000 * 10;
    
    public static final String HEALTH_CHECK_PATH = "/healthcheck";
    public static final String CNV_DYNAMODB_METRICS_TABLE = "CNV-WEBSERVER-METRICS";
    
    /*
     * 
     * 
     * RunningInstances
     * HealthyInstances
     * instancesTrackRecord Map {Instancia, n healthchecks sucesso, n falhas sequidas, n falhas totais}
     * PriorityQueue
     * 
     * time LambdaExpirationData = time;
     * 
     * 
     * 
     * 
     * 
     * 
     * *LB Algorithm
     * 
     * Receives Request;
     * if(!healthCheck()==true) //Means at least 1 instance failed helathcheck
     * 							I think inside healthcheck() the 2 instructions bellow are being done (but maybe better to separate them)
     *							Inside healthcheck (in the end) call autoscaler(2) if any instance failed healthcheck
     *		autocscaler(2)
     *
     * updatesRunningInstances(); //The ones that say "running" in AWS status
     * updatesHealthyInstances();
     * 
     * every(AUTOSCALER_CHECK_PERIODICITY): (Sene, do this "wait" or something else you think we should do here to implement this waiting in threads, in order to not hold the program)
     * 		updateMetricsInMetricList(); (Daniela will do this function)
     * 
     * compute estimateCPUTimeForRequest(); (Daniela will do)
     * 
     * If there is available Instance && (timeWaitingInThatInstance < estimatedCPUtime * MAX_WAITING_REQUEST_RATIO)
     * 		sendToThisInstance();
     * 		autoscaler(0);
     * 
     * else
     * 		if are lambdas available && within expirationTime (expirationTime=lambdaLauchTime + TimeItTakesToSpinVMWorker)
     * 			sendToThisLambda();
     * 
     * 		else
     * 			autoscaler(1);
     * 
     * 
     * 
     * *AS Algorithm
     * 
     *  autoscaler(Integer operation) {
     * 	
     * if (1):
     * 		spin new Instance + Lambda
     * 		place Lambda in LambdaList with its expirationTime
     * 		send requestToLambda();
     * 		
     * 
     * if (0):
     * For(Instances i: priority queue)
     * 
     * 		If there are Instances not working && (LastRequestFinished > VM_WORKER_MAX_TIME_WITHOUT_REQUESTS)
     * 			stopInstance();
     * 			remove instance from trackRecord list
     * 
     * 
     * if (2):
     * For(Instances i: running Instances)
     * 
     * 		If i.sequentialFails >=  VM_WORKER_MAX_SEQUENTIAL_HEALTHCHECK_FAILS 
     * 			|| i.successfulHealthChecks/i.failsAcrossTime >= VM_WORKER_MAX_FAILS_ACROSS_TIME_RATIO:
     * 				List<> requests = getRequestsThatInstanceHasOnItsList();
     * 				Access Threads responsible for requests in list above, and make them run handle again, so they can be reassign to another instance
     * 				i.stopInstance();
     * 				updateRunningInstances(); //needed here or redundant given our code next?
     * 				updateRunningHealthyInstances();
     * 				updateTrackRecord();
     * 				return requests;
     * 
     * 
     * 
     */
    


}
