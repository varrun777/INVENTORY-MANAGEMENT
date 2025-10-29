Simple Inventory (Java 17+ + static HTML)

How to run:
1. Save this folder somewhere, e.g. ~/projects/inventory_simple_java
2. Open a terminal in the folder.
3. Compile:
   javac Main.java Product.java
4. Run:
   java Main
5. Open your browser to http://localhost:8000

Notes:
- Uses only Java 17 standard library (com.sun.net.httpserver).
- Data is kept in memory and resets when you stop the program.
- Frontend uses simple fetch() and sends form-encoded data (no external libs).
