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
        // sample item
        products.add(new Product(nextId++, "Sample Item", 5, 9.99));

        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/", new StaticHandler());
        server.createContext("/api/items", new ItemsHandler());
        server.setExecutor(Executors.newFixedThreadPool(8));
        System.out.println("Server started at http://localhost:8000"); 
        server.start();
    }

    // Serve index.html and static files
    static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/")) path = "/index.html";
            // only serve known files
            if (path.equals("/index.html") || path.equals("/style.css")) {
                InputStream is = Main.class.getResourceAsStream(path);
                if (is == null) {
                    // try file system fallback (when running from folder)
                    File f = new File("./" + path.substring(1));
                    if (f.exists()) is = new FileInputStream(f);
                }
                if (is == null) {
                    String notFound = "404 - not found";
                    exchange.sendResponseHeaders(404, notFound.length());
                    OutputStream os = exchange.getResponseBody();
                    os.write(notFound.getBytes());
                    os.close();
                    return;
                }
                byte[] bytes = is.readAllBytes();
                Headers h = exchange.getResponseHeaders();
                if (path.endsWith(".css")) h.set("Content-Type", "text/css; charset=utf-8");
                else h.set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, bytes.length);
                OutputStream os = exchange.getResponseBody();
                os.write(bytes);
                os.close();
                is.close();
            } else {
                String notFound = "404 - not found";
                exchange.sendResponseHeaders(404, notFound.length());
                OutputStream os = exchange.getResponseBody();
                os.write(notFound.getBytes());
                os.close();
            }
        }
    }

    // Handler for /api/items - supports GET, POST (add), POST with _method=PUT or DELETE for update/delete
    static class ItemsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            if (method.equalsIgnoreCase("GET")) {
                handleList(exchange);
            } else if (method.equalsIgnoreCase("POST")) {
                String body = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))
                        .lines().collect(Collectors.joining("\n"));
                Map<String,String> params = parseUrlEncoded(body);
                String _method = params.getOrDefault("_method", "POST").toUpperCase();
                if (_method.equals("POST")) handleCreate(exchange, params);
                else if (_method.equals("PUT")) handleUpdate(exchange, params);
                else if (_method.equals("DELETE")) handleDelete(exchange, params);
                else sendJson(exchange, 400, mapToJson(Map.of("error","Unsupported _method")));
            } else {
                sendJson(exchange, 405, mapToJson(Map.of("error","Method not allowed")));
            }
        }

        private void handleList(HttpExchange exchange) throws IOException {
            List<String> itemsJson;
            synchronized (products) {
                itemsJson = products.stream().map(Product::toJson).collect(Collectors.toList());
            }
            String json = "[" + String.join(",", itemsJson) + "]";
            sendJson(exchange, 200, json);
        }

        private void handleCreate(HttpExchange exchange, Map<String,String> params) throws IOException {
            String name = params.getOrDefault("name","").trim();
            int quantity = parseInt(params.getOrDefault("quantity","0"));
            double price = parseDouble(params.getOrDefault("price","0"));
            if (name.isEmpty()) {
                sendJson(exchange, 400, mapToJson(Map.of("error","Name required")));
                return;
            }
            Product p;
            synchronized (products) {
                p = new Product(nextId++, name, quantity, price);
                products.add(p);
            }
            sendJson(exchange, 201, p.toJson());
        }

        private void handleUpdate(HttpExchange exchange, Map<String,String> params) throws IOException {
            int id = parseInt(params.getOrDefault("id","0"));
            if (id==0) { sendJson(exchange,400,mapToJson(Map.of("error","id required"))); return; }
            synchronized (products) {
                for (Product p : products) {
                    if (p.getId()==id) {
                        String name = params.get("name"); if (name!=null) p.setName(name);
                        if (params.containsKey("quantity")) p.setQuantity(parseInt(params.get("quantity")));
                        if (params.containsKey("price")) p.setPrice(parseDouble(params.get("price")));
                        sendJson(exchange,200,p.toJson());
                        return;
                    }
                }
            }
            sendJson(exchange,404,mapToJson(Map.of("error","Not found")));
        }

        private void handleDelete(HttpExchange exchange, Map<String,String> params) throws IOException {
            int id = parseInt(params.getOrDefault("id","0"));
            if (id==0) { sendJson(exchange,400,mapToJson(Map.of("error","id required"))); return; }
            synchronized (products) {
                boolean removed = products.removeIf(p->p.getId()==id);
                if (removed) sendJson(exchange,200,mapToJson(Map.of("deleted","true")));
                else sendJson(exchange,404,mapToJson(Map.of("error","Not found")));
            }
        }

        private Map<String,String> parseUrlEncoded(String body) {
            Map<String,String> map = new HashMap<>();
            if (body == null || body.isEmpty()) return map;
            String[] pairs = body.split("&");
            for (String pair : pairs) {
                try {
                    int idx = pair.indexOf('=');
                    if (idx >= 0) {
                        String k = URLDecoder.decode(pair.substring(0, idx), StandardCharsets.UTF_8);
                        String v = URLDecoder.decode(pair.substring(idx+1), StandardCharsets.UTF_8);
                        map.put(k, v);
                    }
                } catch (Exception e) { /* ignore malformed */ }
            }
            return map;
        }

        private int parseInt(String s){ try{return Integer.parseInt(s);}catch(Exception e){return 0;} }
        private double parseDouble(String s){ try{return Double.parseDouble(s);}catch(Exception e){return 0.0;} }

        private void sendJson(HttpExchange ex, int status, String json) throws IOException {
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            Headers h = ex.getResponseHeaders();
            h.set("Content-Type", "application/json; charset=utf-8");
            ex.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(bytes); }
        }

        private String mapToJson(Map<String,String> m){
            StringBuilder sb = new StringBuilder(); sb.append('{');
            boolean first=true;
            for (Map.Entry<String,String> e : m.entrySet()){
                if (!first) sb.append(',');
                sb.append('"').append(escape(e.getKey())).append('"').append(':');
                sb.append('"').append(escape(e.getValue())).append('"');
                first=false;
            }
            sb.append('}'); return sb.toString();
        }

        private String escape(String s){ return s.replace("\\","\\\\").replace("\"","\\\""); }
    }

    // simple JSON for product
    static class ProductJson {
        // not used; product has its own toJson
    }
}
