package cnv.g29.LoadBalancerAndAutoscaler;

import software.amazon.awssdk.services.ec2.model.Instance;

public class InstanceTrackRecord {
	
	private int successfulHealthChecks;
	/* When LB needs to scale down the system, it will use this number divided by 
	 * "failsAcrossTime". The instance with the smallest ratio (=> the poorer performance) will be the one terminated
	 */
	private int sequentialFails;
	/* If the number "subsequentFails" surpasses VM_WORKER_MAX_SUBSEQUENT_HEALTHCHECK_FAILS (defined in "AlgorithmParameters") the instance will be stopped
	 * and the AS will launch another one
	 * */
	
	private int failsAcrossTime;
	
	

	public InstanceTrackRecord() {
		this.successfulHealthChecks = 0;
		this.sequentialFails = 0;
		this.failsAcrossTime = 0;
	}

	public static void main(String[] args) {
		// TODO Auto-generated method stub

	}
	
	//GET
	public int getSuccessfulHealthChecks() {
		return successfulHealthChecks;
	}

	public int getSequentialFails() {
		return sequentialFails;
	}


	public int getFailsAcrossTime() {
		return failsAcrossTime;
	}
	
	//SET
	/*public void setInstance(Instance instance) {
		this.instance = instance;
	}*/
	
	public void incrementSuccessfulHealthChecks() {
		this.successfulHealthChecks += 1;
	}
	
	public void incrementSequentialFails() {
		this.sequentialFails += 1;
	}
	
	public void setSequentialFails(int value) {
		this.sequentialFails = value;
	}

	public void incrementFailsAcrossTime() {
		this.failsAcrossTime += 1;
	}

}
