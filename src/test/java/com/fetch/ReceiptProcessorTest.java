package com.fetch;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import com.fetch.ReceiptProcessor.PointsHandler;
import com.fetch.ReceiptProcessor.PostHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;



public class ReceiptProcessorTest{


    @Test
    void testValidReceipt() throws Exception{
        PostHandler postHandler = new PostHandler();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("simple-receipt.json");
        String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

        JSONObject jsonObject = new JSONObject(json);

        Method validateReceiptMethod = PostHandler.class.getDeclaredMethod("validateReceipt", JSONObject.class);
        validateReceiptMethod.setAccessible(true);

        try{
            validateReceiptMethod.invoke(postHandler,jsonObject);
        } catch(Exception e){
            fail("No exception should be thrown for valid receipt. " + e.getMessage());
        }

    }

    @Test
    void testInvalidReceipt() throws Exception{
        PostHandler postHandler = new PostHandler();
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("invalid-receipt.json");
        
        String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);

        JSONObject jsonObject = new JSONObject(json);

        Method validateReceiptMethod = PostHandler.class.getDeclaredMethod("validateReceipt", JSONObject.class);
        validateReceiptMethod.setAccessible(true);

        try{
            validateReceiptMethod.invoke(postHandler,jsonObject);
            fail("No exception should be thrown for valid receipt.");
        } catch(Exception e){
            assertTrue(e.getCause().toString().contains("Item array is empty."));
        }
        
    }
    
    /*
     * tests the calculatePoints helper method on json files in PointsHandler 
     */
    @Test
    void testCalculatePoints() throws Exception{

        PostHandler postHandler = new PostHandler();

        JSONObject jsonObject = getJson("corner-receipt.json");
        // Extracting values
        String retailer = jsonObject.getString("retailer");
        String purchaseDate = jsonObject.getString("purchaseDate");
        String purchaseTime = jsonObject.getString("purchaseTime");
        JSONArray items = jsonObject.getJSONArray("items");
        String total = jsonObject.getString("total");

        Method calculatePointsMethod = PostHandler.class.getDeclaredMethod("calculatePoints", String.class,String.class,String.class,JSONArray.class,String.class);
        calculatePointsMethod.setAccessible(true);

        int points = (int) calculatePointsMethod.invoke(postHandler, retailer,purchaseDate,purchaseTime,items,total);

        
        assertEquals(109,points);

        
        jsonObject = getJson("target-receipt.json");
        // Extracting values
        retailer = jsonObject.getString("retailer");
        purchaseDate = jsonObject.getString("purchaseDate");
        purchaseTime = jsonObject.getString("purchaseTime");
        items = jsonObject.getJSONArray("items");
        total = jsonObject.getString("total");

        points = (int) calculatePointsMethod.invoke(postHandler, retailer,purchaseDate,purchaseTime,items,total);

        
        assertEquals(28,points);
    }

    @Test
    void testCalculateNamePoints() throws Exception{
        String retailer = "Walgreens";
        int points = 0;
        // 1 point for every alphanumeric character in retailer name
        char[] retailerName = retailer.toCharArray();
        for (char value : retailerName) {
            if (Character.isLetterOrDigit(value)) {
                points++;
            }
        }
        assertEquals(9, points);

        // non alphanumeric test
        retailer = "M&M Corner Market";
        points = 0;
        // 1 point for every alphanumeric character in retailer name
        retailerName = retailer.toCharArray();
        for (char value : retailerName) {
            if (Character.isLetterOrDigit(value)) {
                points++;
            }
        }
        assertEquals(14, points);
    }

    @Test
    void testCalculateRoundAmountPoints()throws Exception{
        // 50 points if the total is a round dollar amount with no cents

        String total = "4.00";
        int points =0;
        BigDecimal totalAmount = new BigDecimal(total);
        if (totalAmount.stripTrailingZeros().scale() <= 0) {
            points += 50;
        }
        assertEquals(50, points);

        points =0;
        total = "4.78";
        totalAmount = new BigDecimal(total);
        if (totalAmount.stripTrailingZeros().scale() <= 0) {
            points += 50;
        }

        assertEquals(0, points);
    }

    @Test
    void testCalculateMultiplePoints() throws Exception{
        // 25 points if total is a multiple of .25
        int points = 0;
        String total = "3.33";
        BigDecimal totalAmount = new BigDecimal(total);
        BigDecimal decimal = new BigDecimal(".25");
        BigDecimal remainder = totalAmount.remainder(decimal);
        if (remainder.compareTo(BigDecimal.ZERO) == 0) {
            points += 25;
        }

        assertEquals(0, points);

        String[] amounts = {"1.00","1.25","1.50","1.75"};
        for( String amount : amounts){
            points = 0;
            totalAmount = new BigDecimal(amount);
            if(totalAmount.remainder(decimal).compareTo(BigDecimal.ZERO) == 0){
                points +=25;
            }
            assertEquals(25, points);

        }

    }

    @Test
    void testCalculatePerTwoPoints() throws Exception{
        //for 1 item ( 0 pts)
        JSONArray jsonArray = new JSONArray();
        jsonArray.put(0,true);
        int points =0;
        points += 5 * (jsonArray.length() / 2);

        assertEquals(0,points);
        // 2 items (5 pts)
        jsonArray.put(1,true);
        points =0;
        points += 5 * (jsonArray.length() / 2);

        assertEquals(5,points);
        // 3 items ( 5 pts )
        jsonArray.put(2,true);
        points =0;
        points += 5 * (jsonArray.length() / 2);

        assertEquals(5,points);
        // 4 items (10 pts)
        jsonArray.put(3,true);
        points =0;
        points += 5 * (jsonArray.length() / 2);

        assertEquals(10,points);

    }

    @Test
    void testCalculateMultipleThreePoints() throws Exception{
        int points =0;
        String jsonString = "{"
                + "\"shortDescription\": \"  Pepsi - 12-oz\","
                + "\"price\": \"1.25\""
                + "}";


        JSONObject item = new JSONObject(jsonString);
        String itemDescription = item.getString("shortDescription").trim();

        if (itemDescription.length() % 3 == 0) {
            BigDecimal price = new BigDecimal(item.getString("price"));
            BigDecimal multiplier = new BigDecimal(.2);

            BigDecimal unrounded = price.multiply(multiplier);
            BigDecimal rounded = unrounded.setScale(0, RoundingMode.UP);
            points += rounded.intValueExact();
        }

        assertEquals(0,points);

        
        points =0;
        jsonString = "{"
                + "\"shortDescription\": \"Pepsi -  128-oz\","
                + "\"price\": \"1.25\""
                + "}";


        item = new JSONObject(jsonString);
        itemDescription = item.getString("shortDescription").trim();

        if (itemDescription.length() % 3 == 0) {
            BigDecimal price = new BigDecimal(item.getString("price"));
            BigDecimal multiplier = new BigDecimal(.2);

            BigDecimal unrounded = price.multiply(multiplier);
            BigDecimal rounded = unrounded.setScale(0, RoundingMode.UP);
            points += rounded.intValueExact();
        }

        assertEquals(1,points);

    }

    @Test
    void testCalculateOddDatePoints() throws Exception{
        int points = 0;
        String purchaseDate = "2024-02-10";
        String[] date = purchaseDate.split("-");
        if (Integer.parseInt(date[2]) % 2 == 1) {
            points += 6;
        }

        assertEquals(0,points);

        points = 0;
        purchaseDate = "2024-02-11";
        date = purchaseDate.split("-");
        if (Integer.parseInt(date[2]) % 2 == 1) {
            points += 6;
        }

        assertEquals(6,points);


    }

    @Test
    void testCalculateHourPoints() throws Exception{

        String[] invalidTimes = {"14:00","16:00","00:01","01:30","8:00"};

        for ( String time : invalidTimes){
            int points = 0;
            String[] timeSplit = time.split(":");
            int hour = Integer.parseInt(timeSplit[0]);
            int min = Integer.parseInt(timeSplit[1]);
            if ((hour == 14 && min > 0) || hour == 15) {
                points += 10;
            }
            assertEquals(0,points);
        }



        String[] validTimes = {"14:01","15:59","14:59","15:01"};

        for ( String time : validTimes){
            int points = 0;
            String[] timeSplit = time.split(":");
            int hour = Integer.parseInt(timeSplit[0]);
            int min = Integer.parseInt(timeSplit[1]);
            if ((hour == 14 && min > 0) || hour == 15) {
                points += 10;
            }
            assertEquals(10,points);
        }




    }

    public JSONObject getJson(String string) throws IOException {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(string);
        String json = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        JSONObject jsonObject = new JSONObject(json);

        return jsonObject;

    }

}
