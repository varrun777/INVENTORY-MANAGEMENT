public class Product {
    private final int id;
    private String name;
    private int quantity;
    private double price;

    public Product(int id, String name, int quantity, double price) {
        this.id = id;
        this.name = name;
        this.quantity = quantity;
        this.price = price;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public int getQuantity() { return quantity; }
    public double getPrice() { return price; }

    public void setName(String name) { this.name = name; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public void setPrice(double price) { this.price = price; }

    public String toJson() {
        return String.format("{\"id\":%d,\"name\":\"%s\",\"quantity\":%d,\"price\":%s}", 
            id, escape(name), quantity, Double.toString(price));
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n");
    }
}
