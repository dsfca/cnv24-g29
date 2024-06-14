package pt.ulisboa.tecnico.cnv.webserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;

public class HealthCheckHandler implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String requestMethod = exchange.getRequestMethod();
        String response;

        if (requestMethod.equals("GET")) {
            // Handle GET request
            response = "Healthcheck endpoint!";
        } else {
            // Handle other request methods (optional)
            // You can send an error response here
            exchange.sendResponseHeaders(405, -1); // Method Not Allowed
            response = "This endpoint only accepts GET requests.";
        }

        byte[] responseBytes = response.getBytes();
        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        exchange.sendResponseHeaders(200, responseBytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(responseBytes);
        os.close();
    }
}
