package pt.ulisboa.tecnico.cnv.imageproc;

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import javax.imageio.ImageIO;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import pt.ulisboa.tecnico.AwsDynamoWriter.AwsDynamoWriter;
import pt.ulisboa.tecnico.cnv.javassist.tools.*;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;


public abstract class ImageProcessingHandler implements HttpHandler, RequestHandler<Map<String,String>, String> {
	
	String CNV_DYNAMODB_METRICS_TABLE = "CNV-WEBSERVER-METRICS";
    abstract BufferedImage process(BufferedImage bi) throws IOException;
    abstract String getEffect();
    abstract int getWidth();
    abstract int getHeight();
    
    public Map<String, AttributeValue> collectMetrics(int width, int height, String effect, long opTime, long cpuTimeUsed) throws UnknownHostException{
    	InetAddress localhost = InetAddress.getLocalHost();
    	String ipAddress = localhost.getHostAddress();
    	int threadId = (int) Thread.currentThread().getId();
    	long[] ICountMetrics = ICount.getMetrics();
    	long nmethods = ICountMetrics[0]; 
    	long nblocks = ICountMetrics[1];
    	long ninsts = ICountMetrics[2];
    	long memoryUsed = ICountMetrics[3];
    	Map<String, AttributeValue> collectedMetric = new HashMap<>();
    	collectedMetric.put("threadId", new AttributeValue(threadId+""));collectedMetric.put("width", new AttributeValue(width+""));
    	collectedMetric.put("height", new AttributeValue(height+""));collectedMetric.put("effect", new AttributeValue(effect));
    	collectedMetric.put("nmethods", new AttributeValue(nmethods+""));collectedMetric.put("nblocks", new AttributeValue(nblocks+""));
    	collectedMetric.put("ninsts", new AttributeValue(ninsts+""));collectedMetric.put("opTime", new AttributeValue(opTime+""));
    	collectedMetric.put("ipAddress", new AttributeValue(ipAddress+""));collectedMetric.put("cpuTimeUsed", new AttributeValue(cpuTimeUsed + ""));
    	collectedMetric.put("memoryUsed", new AttributeValue(memoryUsed+""));
    	return collectedMetric;
    }
    

    private String handleRequest(String inputEncoded, String format) {
        byte[] decoded = Base64.getDecoder().decode(inputEncoded);
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(decoded);
            BufferedImage bi = ImageIO.read(bais);
            bi = process(bi);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bi, format, baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException e) {
            return e.toString();
        }
    }

    @Override
    public void handle(HttpExchange t) throws IOException {
        // Handling CORS
    	
    	ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    	long beforeCpuTime = threadMXBean.getCurrentThreadCpuTime();
    	//long beforeWallClockTime = System.nanoTime();
    	long startTime = System.currentTimeMillis();
    	

        t.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        if (t.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            t.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            t.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
            t.sendResponseHeaders(204, -1);
            return;
        }

        InputStream stream = t.getRequestBody();
        // Result syntax: data:image/<format>;base64,<encoded image>
        String result = new BufferedReader(new InputStreamReader(stream)).lines().collect(Collectors.joining("\n"));
        String[] resultSplits = result.split(",");
        String format = resultSplits[0].split("/")[1].split(";")[0];
        String output = handleRequest(resultSplits[1], format);
        output = String.format("data:image/%s;base64,%s", format, output);
        t.sendResponseHeaders(200, output.length());
        OutputStream os = t.getResponseBody();
        os.write(output.getBytes());
        os.close();
        
        
        long afterCpuTime = threadMXBean.getCurrentThreadCpuTime();
        long cpuTimeUsed = (afterCpuTime - beforeCpuTime) / 1_000_000;     
        long endTime = System.currentTimeMillis();
        long opTime = endTime - startTime;
        Map<String, AttributeValue> collectedMetrics =  collectMetrics(getWidth(), getHeight(), getEffect(), opTime, cpuTimeUsed);
        AwsDynamoWriter.writeMetricsToDynamoDb(collectedMetrics, CNV_DYNAMODB_METRICS_TABLE);
    }

    @Override
    public String handleRequest(Map<String,String> event, Context context) {
        return handleRequest(event.get("body"), event.get("fileFormat"));
    }
}
