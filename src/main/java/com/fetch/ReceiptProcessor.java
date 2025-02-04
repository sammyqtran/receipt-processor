package com.fetch;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.json.JSONArray;
import org.json.JSONObject;

public class ReceiptProcessor {

    private static Map<UUID, Integer> receipts = new HashMap<>();

    public static void main(String[] args) throws IOException {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        // server.createContext("/", new HelloHandler());
        server.createContext("/receipts/process", new PostHandler());
        server.createContext("/receipts", new PointsHandler());
        server.setExecutor(null); // Default executor
        server.start();
        System.out.println("Server started on port " + port);
    }

    static class PointsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");
            System.out.println(path);

            if (parts.length == 4 && "receipts".equals(parts[1]) && "points".equals(parts[3])) {
                try {
                    UUID uuid = UUID.fromString(parts[2]);

                    if (!receipts.containsKey(uuid)) {
                        throw new IllegalArgumentException("UUID not found");
                    }

                    int score = receipts.get(uuid);
                    String response = String.format("{ \"points\": %d }", score);
                    exchange.sendResponseHeaders(200, response.getBytes().length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());

                    os.close();

                } catch (IllegalArgumentException e) {
                    exchange.sendResponseHeaders(400, -1);
                }
            } else {
                exchange.sendResponseHeaders(404, -1);
            }
            exchange.getResponseBody().close();
        }

    }

    static class PostHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {

            String response = "";
            String requestMethod = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if ("/receipts/process".equals(path)) {
                if ("POST".equalsIgnoreCase((requestMethod))) {
                    System.out.println("post received");
                    // use request body to generate the value associated with the receipt
                    InputStream inputStream = exchange.getRequestBody();
                    // String requestBody = new String(inputStream.readAllBytes());

                    String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

                    try {
                        JSONObject jsonObject = new JSONObject(json);
            
                        // Extracting values
                        String retailer = jsonObject.getString("retailer");
                        String purchaseDate = jsonObject.getString("purchaseDate");
                        String purchaseTime = jsonObject.getString("purchaseTime");
                        JSONArray items = jsonObject.getJSONArray("items");
                        String total = jsonObject.getString("total");
            
                        // Printing the values
                        System.out.println("Retailer: " + retailer);
                        System.out.println("Purchase Date: " + purchaseDate);
                        System.out.println("Purchase Time: " + purchaseTime);
                        System.out.println("Total: " + total);
                        System.out.println("Items: ");
                        for (int i = 0; i < items.length(); i++) {
                            JSONObject item = items.getJSONObject(i);
                            System.out.println(
                                    "  - " + item.getString("shortDescription") + ": " + item.getString("price"));
                        }
            
                    } catch (Exception e) {
                        System.out.println("Error parsing JSON: " + e.getMessage());
                        e.printStackTrace();
                    }

                    System.out.println("generating uuid");

                    // generate a universally unique ID and map it to the receipts score
                    UUID uniqueID = UUID.randomUUID();
                    receipts.put(uniqueID, (int) (Math.random() * 100));

                    response = "{\"id\":\"" + uniqueID + "\"}";

                    System.out.println(response);

                    // TODO process the json and throw exception if json malformed
                }
            }

            exchange.sendResponseHeaders(200, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
}
