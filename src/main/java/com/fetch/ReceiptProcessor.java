package com.fetch;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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
        server.createContext("/receipts/process", new PostHandler());
        server.createContext("/receipts", new PointsHandler());
        server.setExecutor(null); 
        server.start();
        System.out.println("Server started on port " + port);
    }

    // helper method to send a properly formatted response with a given status code
    private static void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException{
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, response.getBytes(StandardCharsets.UTF_8).length);
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes(StandardCharsets.UTF_8));
        os.close();
    }
    
    /*
     * Handles GET requests that reach the /receipts/process endpoint
     */
    static class PointsHandler implements HttpHandler {

        /*
         * Overrides HttpHandler's handle method to correctly handle incoming GET
         * requests to retrieve and respond with point data from previously 
         * processed receipts.
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {

            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 404, "{ \"error\": \"No receipt found for that ID.\" }");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            String[] parts = path.split("/");
            System.out.println(path);

            if (parts.length == 4 && "receipts".equals(parts[1]) && "points".equals(parts[3])) {
                try {
                    UUID uuid = UUID.fromString(parts[2]);

                    if (!receipts.containsKey(uuid)) {
                        sendResponse(exchange, 404, "{ \"error\": \"No receipt found for that ID.\" }");
                        return;
                    }

                    int score = receipts.get(uuid);
                    String response = String.format("{ \"points\": %d }", score);
                    sendResponse(exchange, 200, response);

                } catch (IllegalArgumentException e) {
                    sendResponse(exchange, 404, "{ \"error\": \"No receipt found for that ID.\" }");
                }
            } else {
                sendResponse(exchange, 404, "{ \"error\": \"No receipt found for that ID.\" }");
            }
            exchange.getResponseBody().close();
        }



    }

    /*
     * Handles POST requests that reach the /receipts/{id}/points endpoint
     */
    static class PostHandler implements HttpHandler {

        /*
         * Overrides HttpHandler's handle method to correctly handle incoming POST
         * requests, validating the JSON and calculating points based on the rules
         * defined. Responds with the proper code and message upon receiving valid data.
         */
        @Override
        public void handle(HttpExchange exchange) throws IOException {

            String response = "";
            String requestMethod = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if (!"POST".equalsIgnoreCase(requestMethod)){
                response = "{ \"error\": \"The receipt is invalid.\" }";
                sendResponse(exchange,400,response);
                return;
            }
            if (!"/receipts/process".equals(path)) {
                response = "{ \"error\": \"The receipt is invalid.\" }";
                sendResponse(exchange,400,response);
                return;
            }

            System.out.println("post received");

            InputStream inputStream = exchange.getRequestBody();
            String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

            try {
                JSONObject jsonObject = new JSONObject(json);
                validateReceipt(jsonObject);

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

                int points = calculatePoints(retailer, purchaseDate, purchaseTime, items, total);


                System.out.println("generating uuid");

                // generate a universally unique ID and map it to the receipts score
                UUID uniqueID = UUID.randomUUID();
                receipts.put(uniqueID, points);

                response = "{\"id\":\"" + uniqueID + "\"}";
                sendResponse(exchange, 200, response);

                System.out.println(response);
                System.out.println("id: " + uniqueID + " points:  " + points);

            } catch (Exception e) {

                System.out.println("Error: " + e.getMessage());
                e.printStackTrace();

                response = "{ \"error\": \"The receipt is invalid.\" }";
                sendResponse(exchange,400, response);
            }
        }

        /*
         * Helper method to validate a JSONObject holding a receipt. Ensures all fields are populated
         * with properly formatted data. Throws an exception if something is missing.
         * @param jsonObject - the data that will be checked
         * @returns - no return value
         */
        private void validateReceipt(JSONObject jsonObject) throws Exception{
            // check if json has all required fields
            if(!jsonObject.has("retailer") || !jsonObject.has("purchaseDate") || !jsonObject.has("purchaseTime")|| 
                !jsonObject.has("items") || !jsonObject.has("total")){
                throw new IllegalArgumentException("Missing required fields.");
            }
            // check if retailer name matches regex 
            if(!jsonObject.getString("retailer").matches("^[\\w\\s\\-&]+$")){
                throw new IllegalArgumentException("Invalid retailer name.");
            }
            // check if purchase date matches regex 
            try {
                // check if date is valid
                LocalDate.parse(jsonObject.getString("purchaseDate"));
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Invalid date.");
            }
    
            // check if purchase time matches 
            if(!jsonObject.getString("purchaseTime").matches("^([01][0-9]|2[0-3]):([0-5][0-9])$")){
                throw new IllegalArgumentException("Invalid purchase time.");
            }
            // check if items has at least on item
            JSONArray items = jsonObject.getJSONArray("items");
            if(items.length() < 1 ){
                throw new IllegalArgumentException("Item array is empty.");
            }
            // check if items have proper fields and fields formatted correctly
            for( int index =0; index < items.length(); index++){
                JSONObject item = items.getJSONObject(index);
                if(!item.has("shortDescription") || !item.has("price")){
                    throw new IllegalArgumentException("Item is missing fields.");
                }
                if(!item.getString("shortDescription").matches("^[\\w\\s\\-]+$")){
                    throw new IllegalArgumentException("Invalid format for short description");
                }
                if(!item.getString("price").matches("^\\d+\\.\\d{2}$")){
                    throw new IllegalArgumentException("Invalid format for price.");
                }
            }
        }

        /*
         * Calculates the points for a receipt based on the data extracted from the JSONObject. Follows
         * the defined rules.
         * 
         * @returns - an int, that represents the points the receipt receives based on the rules.
         */
        private int calculatePoints(String retailer, String purchaseDate, String purchaseTime, JSONArray items, String total){
            int points = 0;
            // 1 point for every alphanumeric character in retailer name
            char[] retailerName = retailer.toCharArray();
            for (char value : retailerName) {
                if (Character.isLetterOrDigit(value)) {
                    points++;
                }
            }

            // 50 points if the total is a round dollar amount with no cents
            BigDecimal totalAmount = new BigDecimal(total);
            if (totalAmount.stripTrailingZeros().scale() <= 0) {
                points += 50;
            }

            // 25 points if total is a multiple of .25
            BigDecimal decimal = new BigDecimal(".25");
            BigDecimal remainder = totalAmount.remainder(decimal);
            if (remainder.compareTo(BigDecimal.ZERO) == 0) {
                points += 25;
            }

            // 5 points for every two items on the recieipt

            points += 5 * (items.length() / 2);

            // if trimmed length of item description is a multiple of 3
            // price multiplied by .2 and round up to nearest integer

            for (int index = 0; index < items.length(); index++) {
                JSONObject item = items.getJSONObject(index);
                String itemDescription = item.getString("shortDescription").trim();

                if (itemDescription.length() % 3 == 0) {
                    BigDecimal price = new BigDecimal(item.getString("price"));
                    BigDecimal multiplier = new BigDecimal(.2);

                    BigDecimal unrounded = price.multiply(multiplier);
                    BigDecimal rounded = unrounded.setScale(0, RoundingMode.UP);
                    points += rounded.intValueExact();
                }

            }

            // 6 points if the day in the purchase date is odd
            String[] date = purchaseDate.split("-");
            if (Integer.parseInt(date[2]) % 2 == 1) {
                points += 6;
            }

            // 10 points if purchase is after 2:00PM and before 4:00 PM (14:01-15:59)

            String[] time = purchaseTime.split(":");
            int hour = Integer.parseInt(time[0]);
            int min = Integer.parseInt(time[1]);
            if ((hour == 14 && min > 0) || hour == 15) {
                points += 10;
            }
            return points;
        }
    }
}
