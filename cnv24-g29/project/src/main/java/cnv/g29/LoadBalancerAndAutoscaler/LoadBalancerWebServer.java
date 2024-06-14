package cnv.g29.LoadBalancerAndAutoscaler;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import static cnv.g29.AWSConstants.*;

public class LoadBalancerWebServer {


   
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(LB_PORT), 0);
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.createContext("/", new LoadBalancerHandler());
        server.start();
        System.out.println("Load balancer started on port " + LB_PORT);
    }
}
