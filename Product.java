public class Product {
    private int id;
    private String name;
    private int quantity;
    private double price;

    public Product(int id, String name, int quantity, double price) {
        this.id = id;
        this.name = name;
        this.quantity = quantity;
        this.price = price;
    }

    // Getters & Setters
    public int getId() { return id; }
    public String getName() { return name; }
    public int getQuantity() { return quantity; }
    public double getPrice() { return price; }

    public void setName(String name) { this.name = name; }
    public void setQuantity(int q) { this.quantity = q; }
    public void setPrice(double p) { this.price = p; }

    public String toJson() {
        return String.format(
                "{\"id\":%d,\"name\":\"%s\",\"quantity\":%d,\"price\":%.2f}",
                id, name, quantity, price
        );
    }
}
