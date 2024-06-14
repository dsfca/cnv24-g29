package cnv.g29.LoadBalancerAndAutoscaler;

import cnv.g29.EC2.EC2Manager;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.Instance;

import java.util.ArrayList;
import java.util.List;

import static cnv.g29.AWSConstants.*;

public class Autoscaler {

    private final List<Instance> healthyInstances = new ArrayList<>();
    private final Ec2Client ec2Client = EC2Manager.createEc2Client(AWS_DEFAULT_REGION, DEFAULT_CREDENTIALS_PROVIDER);

    public String spinEC2Instance() {
        return EC2Manager.runEC2Instance(ec2Client, DEFAULT_SECURITY_GROUP, EC2Manager.createSecurityGroup(ec2Client));
    }

    public void stopEC2Instance(String instanceId) {
        EC2Manager.stopInstance(ec2Client, instanceId);
    }

}
