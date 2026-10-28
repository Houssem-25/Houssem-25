package com.scanmart.pos;

class Product {
    final String barcode;
    String name;
    long price; // minor units (cents)
    int stock;

    Product(String barcode, String name, long price, int stock) {
        this.barcode = barcode;
        this.name = name;
        this.price = price;
        this.stock = stock;
    }
}
