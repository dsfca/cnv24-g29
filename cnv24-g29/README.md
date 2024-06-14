## SpecialVFX@Cloud

This project contains three sub-projects:

1. `raytracer` - the Ray Tracing workload
2. `imageproc` - the BlurImage and EnhanceImage workloads
3. `webserver` - the web server exposing the functionality of the workloads

Refer to the `README.md` files of the sub-projects to get more details about each specific sub-project.

### How to build everything

1. Make sure your `JAVA_HOME` environment variable is set to Java 11+ distribution
2. Run `mvn clean package`
3. How to run locally
    To run Web Server locally with javassist, execute this command:

    java -cp webserver/target/webserver-1.0.0-SNAPSHOT-jar-with-dependencies.jar -javaagent:JavassistWrapper/target/JavassistWrapper-1.0-jar-with-dependencies.jar pt.ulisboa.tecnico.cnv.webserver.WebServer

4. # AMI
Our image was created based on the instance configuration example of the lab (as well as the security group associated). <br>
The image contains the "Special VFX Studio" project inside, and runs the WebServer automatically after being initialized through the following configuration in the <code>/etc/rc.local</code> file:<br>
<code>
#!/bin/sh -e
java -cp webserver/target/webserver-1.0.0-SNAPSHOT-jar-with-dependencies.jar -javaagent:JavassistWrapper/target/JavassistWrapper-1.0-jar-with-dependencies.jar pt.ulisboa.tecnico.cnv.webserver.WebServer &> /tmp/webserver.log
</code>

# Load Balancer
The load Balancer was also configured based on the lab example and the health check ping path specified to "/".

# Auto Scaler
We also created a *lauch template*, an *Auto Scaling Group*, the two *CloudWatch* alarms and the *Dynamic Scaling Policy* based on the examples in the lab.
<br>
<br>
<br>
<br>
# Algorithms
## Auto Scaler
### Introduction
The Auto Scaler component is accountable for the number of web server nodes active at any given moment. The goal is to achieve a good tradeoff between performance and cost.
The AS should detect that the VM workers are overloaded and start new instances and, accordingly, reduce the number of nodes when the load decreases. <br>
### Algorithm
After the initialization of the Auto Scaler, it will interact with the Metric Storage System at regular intervals to monitor the state of the active instances. The main metrics that we are monitoring are:
1. Health checks: the Auto Scaler monitors the healthiness of the EC2 instances at regular intervals (e.g every 5 sec) by attempting to connect to a specific endpoint /healthcheck.
2. Resource utilization: mainly CPU and memory usage to avoid the overload on a single instance.
3. Average response time: assuming a uniform distribution of the incoming requests, if the average response time is increasing it will represent that an instance is becoming overloaded. <br>

Using the metrics discussed above, the Auto Scaler component will take the following actions: <br>
1. If health checks on a generic instance are failing at least 3 times, spin another instance.
2. If the active instances have a resource utilization, in terms of memory or CPU usage, close to 80%, increase the number of instances by one.
On the other hand, if the instances show a resource utilization under 20%, decrease them.
3. If the average response time shows a significant increase, launch a new instance, since it means that it is overloading or there is a spike in the request number that the instance is handling.

## Load balancer
### Introduction
A Load Balancer plays a vital role in distributing traffic efficiently across multiple web servers (or instances) within the application. It acts as a single point of entry for incoming user requests and employs algorithms to route those requests to healthy and available servers.
### Algorithm
After the initialization of the Load Balancer, it will read the following metrics stored in the Metric Storage System regularly in order to orchestrate the future requests.
It's possible to distinguish metrics collected at instance level, where each EC2 instance is storing metrics to the Metric Storage System, and at system level.
At instance level, we will estimate the request complexity by monitoring:
1. Resource utilization: mainly CPU and memory usage to avoid the overload on a single instance.
2. Average execution time: by collecting timestamps at the entry and exit points of methods, it will be possible to compute the time difference.
3. Request throughput: how many requests have been processed per unit of time, from when the request arrives to when the response is sent.
4. Active Connections: this metric can describe the current load of a specific instance
5. Concurrency level: track the number of concurrent requests being processed at any given time. Javaassist can be used to instrument code to measure concurrency-related metrics, such as the number of threads active at a given time or the frequency of thread context switches.<br><br>

Whereas at system level, in other words considering the current set of operating EC2 instances, we are computing: <br>
1. Health checks: the Load Balancer monitors the healthiness of the EC2 instances at regular intervals (e.g every 5 sec) by attempting to connect to a specific endpoint /healthcheck.
If the instance's healthcheck is not replying, the LB will choose another instance to handle it. <br><br>

Based on the availability of those metrics, the Load Balancer will take the following actions, ranked from top to bottom: <br>
1. If the health check on the instance X has failed at least 3 times in a row, mark the instance as unhealthy and exclude it by re-routing the requests to healthy instances.
It will prevent that an user waits too much for the response or that a chain of errors occurs.
2. When a request arrives, select a healthy instance having the least resource utilization.
3. If request throughput is poor and the average execution time is less than a fixed threshold, trigger a lambda invocation.
4. If there are instances with the same amount of free resources, send the request to the one that shows the least number of active connections.

