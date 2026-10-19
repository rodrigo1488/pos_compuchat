package com.compumais.poscompuchat;

import com.compumais.poscompuchat.data.CatalogModels;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CartLineAddonsTest {
    @Test
    public void pizzaMaisBordaFecha95() {
        CatalogModels.CartLine line = new CatalogModels.CartLine();
        line.productValue = 85;
        line.quantity = 1;
        CatalogModels.CartAddon borda = new CatalogModels.CartAddon();
        borda.label = "Borda";
        borda.value = 10;
        borda.quantity = 1;
        line.addons.add(borda);
        assertEquals(10.0, line.addonsTotal(), 0.001);
        assertEquals(95.0, line.lineTotal(), 0.001);
    }
}
