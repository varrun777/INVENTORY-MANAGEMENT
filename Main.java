import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class Main {
    private static final List<Product> products = Collections.synchronizedList(new ArrayList<>());
    private static int nextId = 1;

    public static void main(String[] args) throws Exception {

        products.add(new Product(nextId++, "Sample Item", 10, 100.0));

        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/api/items", new ItemsHandler());
        server.setExecutor(Executors.newFixedThreadPool(5));

        System.out.println("✅ Server running at: http://localhost:8000/api/items");
        server.start();
    }


    static class ItemsHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();

            if (method.equalsIgnoreCase("GET")) {
                handleList(exchange);
            } else if (method.equalsIgnoreCase("POST")) {
                handleCreateUpdateDelete(exchange);
            } else {
                sendJson(exchange, 405, "{\"error\":\"Method not allowed\"}");
            }
        }

        private void handleList(HttpExchange exchange) throws IOException {
            String json;
            synchronized (products) {
                json = products.stream().map(Product::toJson)
                        .collect(Collectors.joining(",", "[", "]"));
            }
            sendJson(exchange, 200, json);
        }

        private void handleCreateUpdateDelete(HttpExchange exchange) throws IOException {
            String body = new BufferedReader(
                    new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8)
            ).lines().collect(Collectors.joining("\n"));

            Map<String, String> params = parseParams(body);
            String action = params.getOrDefault("_method", "POST").toUpperCase();

            switch (action) {
                case "POST" -> handleCreate(exchange, params);
                case "PUT" -> handleUpdate(exchange, params);
                case "DELETE" -> handleDelete(exchange, params);
                default -> sendJson(exchange, 400, "{\"error\":\"Invalid _method\"}");
            }
        }

        private void handleCreate(HttpExchange exchange, Map<String, String> params) throws IOException {
            String name = params.getOrDefault("name", "").trim();
            int qty = parseInt(params.get("quantity"));
            double price = parseDouble(params.get("price"));

            if (name.isEmpty()) {
                sendJson(exchange, 400, "{\"error\":\"Name required\"}");
                return;
            }

            Product p;
            synchronized (products) {
                p = new Product(nextId++, name, qty, price);
                products.add(p);
            }
            sendJson(exchange, 201, p.toJson());
        }

        private void handleUpdate(HttpExchange exchange, Map<String, String> params) throws IOException {
            int id = parseInt(params.get("id"));
            if (id == 0) {
                sendJson(exchange, 400, "{\"error\":\"id required\"}");
                return;
            }

            synchronized (products) {
                for (Product p : products) {
                    if (p.getId() == id) {
                        if (params.containsKey("name")) p.setName(params.get("name"));
                        if (params.containsKey("quantity")) p.setQuantity(parseInt(params.get("quantity")));
                        if (params.containsKey("price")) p.setPrice(parseDouble(params.get("price")));
                        sendJson(exchange, 200, p.toJson());
                        return;
                    }
                }
            }
            sendJson(exchange, 404, "{\"error\":\"Not found\"}");
        }

        private void handleDelete(HttpExchange exchange, Map<String, String> params) throws IOException {
            int id = parseInt(params.get("id"));

            synchronized (products) {
                boolean removed = products.removeIf(p -> p.getId() == id);
                if (removed) sendJson(exchange, 200, "{\"deleted\":true}");
                else sendJson(exchange, 404, "{\"error\":\"Not found\"}");
            }
        }

        private Map<String, String> parseParams(String body) {
            Map<String, String> map = new HashMap<>();
            if (body == null || body.isEmpty()) return map;

            for (String pair : body.split("&")) {
                String[] parts = pair.split("=");
                if (parts.length == 2) {
                    map.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                            URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
                }
            }
            return map;
        }

        private int parseInt(String s) {
            try { return Integer.parseInt(s); } catch (Exception e) { return 0; }
        }
        private double parseDouble(String s) {
            try { return Double.parseDouble(s); } catch (Exception e) { return 0.0; }
        }

        private void sendJson(HttpExchange ex, int status, String data) throws IOException {
            byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json");
            ex.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
