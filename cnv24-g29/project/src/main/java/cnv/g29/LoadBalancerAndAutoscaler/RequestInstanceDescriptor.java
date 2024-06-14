package cnv.g29.LoadBalancerAndAutoscaler;

import com.sun.net.httpserver.HttpExchange;
import software.amazon.awssdk.services.ec2.model.Instance;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class RequestInstanceDescriptor implements Comparable<RequestInstanceDescriptor> {
    private List<HttpExchange> requests = new ArrayList<>();
    private List<Integer> requestsDuration = new ArrayList<>();
    private Instance instance;
    private Instant totalCpuTime;


    public RequestInstanceDescriptor(Instance instance) {
        this.instance = instance;
        this.totalCpuTime = Instant.now();
    }

    public List<HttpExchange> getRequests() {
        return requests;
    }
    
    public List<Integer> getRequestsDuration() {
        return requestsDuration;
    }

    public void addRequest(HttpExchange request) {
        this.requests.add(request);
    }
    
    public void removeRequest(HttpExchange request) {
        this.requests.remove(request);
    }
    
    public void addRequestDuration(Integer timeMillis) {
        this.requestsDuration.add(timeMillis);
    }
    
    public void removeRequestDuration(Integer index) {
        this.requestsDuration.remove(index);
    }

    public Instance getInstance() {
        return instance;
    }

    public void setInstance(Instance instance) {
        this.instance = instance;
    }

    public Instant getTotalCpuTime() {
        return totalCpuTime;
    }

    public void setTotalCpuTime(Instant totalCpuTime) {
        this.totalCpuTime = totalCpuTime;
    }

    @Override
    public int compareTo(RequestInstanceDescriptor other) {
        return this.totalCpuTime.compareTo(other.totalCpuTime);
    }
}



