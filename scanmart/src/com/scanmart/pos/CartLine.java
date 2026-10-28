package com.scanmart.pos;

class CartLine {
    final Product product;
    int qty;

    CartLine(Product product, int qty) {
        this.product = product;
        this.qty = qty;
    }

    long total() {
        return product.price * qty;
    }
}
