# Receipt Processor

This project is a simple java server that processes receipts, calculating points on defined rules,
and returning an ID that can be used to lookup points from older receipts.

## Running the server with Docker

### 1. Build the Docker image.

Navigate to the folder containing the Dockerfile and run this command to create a Docker image.

```
docker build -t receipt-processor .
```

### 2. Run the Docker container.

After the image is built, it can be run in a Docker container. This command will run the server on 
port 8080 of your local machine. (localhost:8080)

```
docker run -p 8080:8080 receipt-processor
```

### 3. Make a request.

Use an http client to make post requests to {localhost:8080}/receipts/process with the properly
formatted JSON.

This is an example using cURL.
```
curl -v -X POST http://localhost:8080/receipts/process \
-H "Content-Type: application/json" \
-d '{
  "retailer": "M&M Corner Market",
  "purchaseDate": "2022-03-20",
  "purchaseTime": "14:33",
  "items": [
    {"shortDescription": "Gatorade", "price": "2.25"},
    {"shortDescription": "Gatorade", "price": "2.25"},
    {"shortDescription": "Gatorade", "price": "2.25"},
    {"shortDescription": "Gatorade", "price": "2.25"}
  ],
  "total": "9.00"
}'
```

Let's say it returned this output.
```
{"id":"dbc1798c-a25c-4a26-9991-92f872dbda58"}
```

To retrieve the points you can use the /receipts/{id}/points endpoint.

```
curl -v http://localhost:8080/receipts/dbc1798c-a25c-4a26-9991-92f872dbda58/points
```

Response will be given in this format 

```
{ "points" : "109" }
```