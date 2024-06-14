package cnv.g29.lambda;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.Runtime;
import software.amazon.awssdk.services.lambda.model.*;
import software.amazon.awssdk.services.lambda.waiters.LambdaWaiter;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cnv.g29.AWSConstants.AWS_DEFAULT_REGION;
import static cnv.g29.AWSConstants.DEFAULT_CREDENTIALS_PROVIDER;

public class LambdaManager {
    private final static ObjectMapper mapper = new ObjectMapper();

    private static final LambdaClient lambdaClient = LambdaClient.builder()
            .region(AWS_DEFAULT_REGION)
            .credentialsProvider(DEFAULT_CREDENTIALS_PROVIDER)
            .build();

    private static Map<String, String> lambda_functions = Map.of("/raytracer", "raytracer-lambda",
            "/blurimage", "blurimage-lambda", "/enhanceimage", "enhanceimage-lambda");

    public static void listFunctions() {

        try {
            ListFunctionsResponse functionResult = lambdaClient.listFunctions();
            List<FunctionConfiguration> list = functionResult.functions();

            for (FunctionConfiguration config : list) {
                System.out.println("The function name is " + config.functionName());
            }

        } catch (LambdaException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    public static void createLambdaFunction(
            String functionName,
            String filePath,
            String role,
            String handler) {

        try {
            LambdaWaiter waiter = lambdaClient.waiter();
            InputStream is = new FileInputStream(filePath);
            SdkBytes fileToUpload = SdkBytes.fromInputStream(is);

            FunctionCode code = FunctionCode.builder()
                    .zipFile(fileToUpload)
                    .build();

            CreateFunctionRequest functionRequest = CreateFunctionRequest.builder()
                    .functionName(functionName)
                    .description("Created by the Lambda Java API")
                    .code(code)
                    .handler(handler)
                    .runtime(Runtime.JAVA17)
                    .role(role)
                    .build();

            // Create a Lambda function using a waiter.
            CreateFunctionResponse functionResponse = lambdaClient.createFunction(functionRequest);
            GetFunctionRequest getFunctionRequest = GetFunctionRequest.builder()
                    .functionName(functionName)
                    .build();
            WaiterResponse<GetFunctionResponse> waiterResponse = waiter.waitUntilFunctionExists(getFunctionRequest);
            waiterResponse.matched().response().ifPresent(System.out::println);
            System.out.println("The function ARN    " + functionResponse.functionArn());

        } catch (LambdaException | FileNotFoundException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
    }

    public static String invokeFunction(String path, Map<String, String> parameters) {
        String lambdaFunctionname = lambda_functions.get(path);
        if (Objects.isNull(lambdaFunctionname))
            return null;
        InvokeResponse res = null;
        try {
            SdkBytes payload = SdkBytes.fromString(getEntireRequestAsString(parameters), Charset.defaultCharset());
            InvokeRequest request = InvokeRequest.builder()
                    .functionName(lambdaFunctionname)
                    .payload(payload)
                    .build();

            res = lambdaClient.invoke(request);
            String responseValue = res.payload().asUtf8String();
            responseValue = String.format("data:image/bmp;base64,%s", responseValue.replace("\"", ""));
            System.out.println(responseValue);
            return responseValue;

        } catch (LambdaException | JsonProcessingException e) {
            System.err.println(e.getMessage());
            return null;
        }
    }

    private static String getEntireRequestAsString(/*Map<String, Object> parameters*/ Map<String, String> parameters) throws JsonProcessingException {
       /* String query = requestedUri.getRawQuery();
        Map<String, String> parameters = queryToMap(query);

        byte[] input = null;
        if (body.containsKey("scene"))
            input = ((String) body.get("scene")).getBytes();
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
        parameters.put("texmap", texmapBase64);*/
        //try {
        return mapper.writeValueAsString(parameters);
        //} catch (JsonProcessingException e) {
        //    throw new RuntimeException(e);
        //}
    }

/*    private static Map<String, String> queryToMap(String query) {
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
    }*/

}
