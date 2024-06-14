package pt.ulisboa.tecnico.cnv.raytracer;

import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import net.bytebuddy.description.type.TypeDescription.Generic.Visitor.Substitutor.ForTokenNormalization;
import pt.ulisboa.tecnico.AwsDynamoWriter.AwsDynamoWriter;
import pt.ulisboa.tecnico.cnv.javassist.tools.ICount;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import java.util.UUID;

public class RaytracerHandler implements HttpHandler, RequestHandler<Map<String, String>, String> {
	private static String CNV_DYNAMODB_METRICS_TABLE = "CNV-WEBSERVER-METRICS";

    private final static ObjectMapper mapper = new ObjectMapper();
    
    Map<String, AttributeValue> collectedMetric = new HashMap<>();

    @Override
    public void handle(HttpExchange he) throws IOException {
    	ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
    	long beforeCpuTime = threadMXBean.getCurrentThreadCpuTime();
    	//long beforeWallClockTime = System.nanoTime();
    	long startTime = System.currentTimeMillis();
    	
    	
        // Handling CORS
        he.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        if (he.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            he.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, OPTIONS");
            he.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
            he.sendResponseHeaders(204, -1);
            return;
        }

        // Parse request
        URI requestedUri = he.getRequestURI();
        String query = requestedUri.getRawQuery();
        Map<String, String> parameters = queryToMap(query);

        int scols = Integer.parseInt(parameters.get("scols"));
        int srows = Integer.parseInt(parameters.get("srows"));
        int wcols = Integer.parseInt(parameters.get("wcols"));
        int wrows = Integer.parseInt(parameters.get("wrows"));
        int coff = Integer.parseInt(parameters.get("coff"));
        int roff = Integer.parseInt(parameters.get("roff"));
        Main.ANTI_ALIAS = Boolean.parseBoolean(parameters.getOrDefault("aa", "false"));
        Main.MULTI_THREAD = Boolean.parseBoolean(parameters.getOrDefault("multi", "false"));

        InputStream stream = he.getRequestBody();
        Map<String, Object> body = mapper.readValue(stream, new TypeReference<>() {});

        byte[] input = ((String) body.get("scene")).getBytes();
        byte[] texmap = null;
        if (body.containsKey("texmap")) {
            // Convert ArrayList<Integer> to byte[]
            ArrayList<Integer> texmapBytes = (ArrayList<Integer>) body.get("texmap");
            texmap = new byte[texmapBytes.size()];
            for (int i = 0; i < texmapBytes.size(); i++) {
                texmap[i] = texmapBytes.get(i).byteValue();
            }
        }

        byte[] result = handleRequest(input, texmap, scols, srows, wcols, wrows, coff, roff);
        String response = String.format("data:image/bmp;base64,%s", Base64.getEncoder().encodeToString(result));

        he.sendResponseHeaders(200, response.length());
        OutputStream os = he.getResponseBody();
        os.write(response.getBytes());
        os.close();
        
        
        long afterCpuTime = threadMXBean.getCurrentThreadCpuTime();
        //long afterWallClockTime = System.nanoTime();
        long cpuTimeUsed = (afterCpuTime - beforeCpuTime)/ 1_000_000;
        //long elapsedTime = afterWallClockTime - beforeWallClockTime;
        //double cpuUtilization = (double) cpuTimeUsed / (elapsedTime);
        long endTime = System.currentTimeMillis();
        long opTime = endTime - startTime;
        Map<String, AttributeValue> collectedMetrics = collectMetrics(scols, srows, wcols, wrows, texmap, opTime, cpuTimeUsed);
        collectedMetrics.put("opTime", new AttributeValue(opTime + ""));
        AwsDynamoWriter.writeMetricsToDynamoDb(collectedMetrics, CNV_DYNAMODB_METRICS_TABLE);
    }

    public Map<String, String> queryToMap(String query) {
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

    private byte[] handleRequest(byte[] input, byte[] texmap, int scols, int srows, int wcols, int wrows, int coff, int roff) {
        try {
            RayTracer rayTracer = new RayTracer(scols, srows, wcols, wrows, coff, roff);
            rayTracer.readScene(input, texmap);
            BufferedImage image = rayTracer.draw();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "bmp", baos);
            collectedMetric.put("lights", new AttributeValue(rayTracer.getLightsSize() + ""));collectedMetric.put("pigments", new AttributeValue(rayTracer.getPigmentsSize() +""));
            collectedMetric.put("finishes", new AttributeValue(rayTracer.getFinishesSize() + ""));collectedMetric.put("shapes", new AttributeValue(rayTracer.getShapesSize() +""));
            return baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
            return e.getMessage().getBytes();
        }
    }
    
    public Map<String, AttributeValue> collectMetrics(int scol, int srows, int wcols, int wrows, byte[] texmap, long opTime, long cpuTimeUsed) throws UnknownHostException{
    	InetAddress localhost = InetAddress.getLocalHost();
    	String ipAddress = localhost.getHostAddress();
    	String texture = texmap == null ? "False" : "True";
    	int threadId = (int) Thread.currentThread().getId();
    	long[] ICountMetrics = ICount.getMetrics();
    	long nmethods = ICountMetrics[0]; 
    	long nblocks = ICountMetrics[1];
    	long ninsts = ICountMetrics[2]; //
    	long memoryUsed = ICountMetrics[3];
    	collectedMetric.put("scols", new AttributeValue(scol+""));collectedMetric.put("srows", new AttributeValue(srows+""));
    	collectedMetric.put("wcols", new AttributeValue(wcols+""));collectedMetric.put("wrows", new AttributeValue(wrows+""));
    	collectedMetric.put("threadId", new AttributeValue(threadId+""));collectedMetric.put("effect", new AttributeValue("raytracer"));
    	collectedMetric.put("nmethods", new AttributeValue(nmethods+""));collectedMetric.put("nblocks", new AttributeValue(nblocks+""));
    	collectedMetric.put("ninsts", new AttributeValue(ninsts+""));collectedMetric.put("texture", new AttributeValue(texture));
    	collectedMetric.put("ipAddress", new AttributeValue(ipAddress+""));collectedMetric.put("cpuTimeUsed", new AttributeValue(cpuTimeUsed + ""));
    	collectedMetric.put("memoryUsed", new AttributeValue(memoryUsed+""));
    	//ICount.resetMetrics();
    	return collectedMetric;
    }

    @Override
    public String handleRequest(Map<String,String> event, Context context) {
        Main.ANTI_ALIAS = Boolean.parseBoolean(event.getOrDefault("aa", "false"));
        Main.MULTI_THREAD = Boolean.parseBoolean(event.getOrDefault("multi", "false"));
        int scols = Integer.parseInt(event.get("scols"));
        int srows = Integer.parseInt(event.get("srows"));
        int wcols = Integer.parseInt(event.get("wcols"));
        int wrows = Integer.parseInt(event.get("wrows"));
        int coff = Integer.parseInt(event.get("coff"));
        int roff = Integer.parseInt(event.get("roff"));
        Base64.Decoder decoder = Base64.getDecoder();
        byte[] input = decoder.decode(event.get("input"));
        byte[] texmap = event.containsKey("texmap") ? decoder.decode(event.get("texmap")) : null;
        byte[] byteArrayResult = handleRequest(input, texmap, scols, srows, wcols, wrows, coff, roff);
        return Base64.getEncoder().encodeToString(byteArrayResult);
    }
}
