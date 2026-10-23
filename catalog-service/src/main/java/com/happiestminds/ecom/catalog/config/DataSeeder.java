package com.happiestminds.ecom.catalog.config;

import com.happiestminds.ecom.catalog.domain.Category;
import com.happiestminds.ecom.catalog.domain.Product;
import com.happiestminds.ecom.catalog.domain.ProductPrice;
import com.happiestminds.ecom.catalog.repository.CategoryRepository;
import com.happiestminds.ecom.catalog.repository.ProductPriceRepository;
import com.happiestminds.ecom.catalog.repository.ProductRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

@Component
public class DataSeeder implements CommandLineRunner {

    private final CategoryRepository categories;
    private final ProductRepository products;
    private final ProductPriceRepository prices;

    public DataSeeder(CategoryRepository categories, ProductRepository products, ProductPriceRepository prices) {
        this.categories = categories;
        this.products = products;
        this.prices = prices;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Category laptops = category("Laptops", "Laptop computers");
        Category accessories = category("Accessories", "Computer accessories");
        seed("11111111-0000-0000-0000-000000000001", "LAP-001", "ProBook 14", laptops, "55000.00");
        seed("11111111-0000-0000-0000-000000000002", "LAP-002", "UltraSlim 13", laptops, "72999.99");
        seed("11111111-0000-0000-0000-000000000003", "ACC-001", "USB-C Hub", accessories, "1499.00");
        seed("11111111-0000-0000-0000-000000000004", "ACC-002", "Wireless Mouse", accessories, "899.00");
    }

    private Category category(String name, String description) {
        return categories.findByName(name).orElseGet(() -> categories.save(new Category(name, description, null)));
    }

    private void seed(String id, String sku, String name, Category category, String amount) {
        if (products.existsBySku(sku)) {
            return;
        }
        Product product = products.save(new Product(id, sku, name, name, category, true));
        prices.save(new ProductPrice(product, new BigDecimal(amount), "INR", Instant.now().minusSeconds(60)));
    }
}
