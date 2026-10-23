package com.happiestminds.ecom.inventory.config;

import com.happiestminds.ecom.inventory.domain.InventoryStock;
import com.happiestminds.ecom.inventory.repository.InventoryStockRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements CommandLineRunner {

    private final InventoryStockRepository stocks;

    public DataSeeder(InventoryStockRepository stocks) {
        this.stocks = stocks;
    }

    @Override
    public void run(String... args) {
        seed("11111111-0000-0000-0000-000000000001", 5);   // LAP-001
        seed("11111111-0000-0000-0000-000000000002", 1);   // LAP-002, the last laptop
        seed("11111111-0000-0000-0000-000000000003", 50);  // ACC-001
        seed("11111111-0000-0000-0000-000000000004", 100); // ACC-002
    }

    private void seed(String productId, int available) {
        if (!stocks.existsById(productId)) {
            stocks.save(InventoryStock.of(productId, available, 0));
        }
    }
}
