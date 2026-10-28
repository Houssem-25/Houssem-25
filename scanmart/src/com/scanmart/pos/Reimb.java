package com.scanmart.pos;

/**
 * Tiers-payant maths. The insurer reimburses a percentage of the reference tariff (TR); if the
 * shelf price is higher than the TR the patient pays the difference, if lower the percentage
 * applies to the shelf price. Rates and tariffs are data entered per product, never hard-coded.
 */
class Reimb {
    static final String[] FUNDS = {"CNAS", "CASNOS"};

    /** Effective rate in percent for a product (0 = not reimbursable). */
    static int rate(Product p, boolean exempt) {
        if (p.reimb <= 0) return 0;
        return exempt ? 100 : Math.min(p.reimb, 100);
    }

    /** Amount the insurer pays for ONE unit, in minor units. */
    static long perUnit(Product p, boolean exempt) {
        int rate = rate(p, exempt);
        if (rate == 0) return 0;
        long base = p.tr > 0 ? Math.min(p.price, p.tr) : p.price;
        return Math.round(base * rate / 100.0);
    }
}
