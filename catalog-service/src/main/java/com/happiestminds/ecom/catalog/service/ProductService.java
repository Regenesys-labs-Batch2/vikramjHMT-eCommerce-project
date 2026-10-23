package com.happiestminds.ecom.catalog.service;

import com.happiestminds.ecom.catalog.api.ApiError;
import com.happiestminds.ecom.catalog.api.dto.PageResponse;
import com.happiestminds.ecom.catalog.api.dto.PriceDto;
import com.happiestminds.ecom.catalog.api.dto.ProductRequest;
import com.happiestminds.ecom.catalog.api.dto.ProductResponse;
import com.happiestminds.ecom.catalog.domain.Category;
import com.happiestminds.ecom.catalog.domain.Product;
import com.happiestminds.ecom.catalog.domain.ProductPrice;
import com.happiestminds.ecom.catalog.repository.CategoryRepository;
import com.happiestminds.ecom.catalog.repository.ProductPriceRepository;
import com.happiestminds.ecom.catalog.repository.ProductRepository;
import com.happiestminds.ecom.catalog.repository.ProductSpecifications;
import com.happiestminds.ecom.catalog.service.exception.BadRequestException;
import com.happiestminds.ecom.catalog.service.exception.ConflictException;
import com.happiestminds.ecom.catalog.service.exception.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ProductService {

    static final String CURRENCY = "INR";
    static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORTABLE = Set.of("name", "sku", "createdAt", "updatedAt", "active");

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ProductPriceRepository prices;

    public ProductService(ProductRepository products, CategoryRepository categories, ProductPriceRepository prices) {
        this.products = products;
        this.categories = categories;
        this.prices = prices;
    }

    @Transactional
    public ProductResponse create(ProductRequest request) {
        BigDecimal amount = validatePrice(request.price());
        String sku = request.sku().trim().toUpperCase(Locale.ROOT);
        if (products.existsBySku(sku)) {
            throw skuTaken(sku);
        }
        Category category = findCategory(request.categoryId());
        Product product = new Product(sku, request.name().trim(), request.description(), category,
                request.active() == null || request.active());
        Product saved;
        try {
            // Product has an assigned id, so save() merges; always continue with the returned managed copy.
            saved = products.saveAndFlush(product);
        } catch (DataIntegrityViolationException e) {
            throw skuTaken(sku);
        }
        prices.save(new ProductPrice(saved, amount, CURRENCY, Instant.now()));
        return toResponses(List.of(saved)).get(0);
    }

    @Transactional
    public ProductResponse update(String id, ProductRequest request) {
        BigDecimal amount = validatePrice(request.price());
        Product product = products.findById(id)
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));
        product.update(request.name().trim(), request.description(), findCategory(request.categoryId()),
                request.active() == null || request.active());
        Instant now = Instant.now();
        List<ProductPrice> current = prices.findCurrent(List.of(id), now);
        if (current.stream().noneMatch(p -> p.getAmount().compareTo(amount) == 0)) {
            current.forEach(p -> p.closeAt(now));
            prices.save(new ProductPrice(product, amount, CURRENCY, now));
        }
        return toResponses(List.of(products.save(product))).get(0);
    }

    @Transactional(readOnly = true)
    public ProductResponse get(String id) {
        Product product = products.findById(id)
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));
        return toResponses(List.of(product)).get(0);
    }

    @Transactional(readOnly = true)
    public ProductResponse getBySku(String sku) {
        Product product = products.findBySku(sku.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));
        return toResponses(List.of(product)).get(0);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String q, String categoryId, Boolean active, BigDecimal minPrice,
                                                BigDecimal maxPrice, Integer page, Integer size, String sort) {
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? 20 : size;
        if (pageNumber < 0 || pageSize < 1) {
            throw new BadRequestException("VALIDATION_ERROR", "page must be >= 0 and size must be >= 1");
        }
        PageRequest pageable = PageRequest.of(pageNumber, Math.min(pageSize, MAX_PAGE_SIZE), parseSort(sort));
        Page<Product> result = products.findAll(
                ProductSpecifications.search(q, categoryId, active, minPrice, maxPrice, Instant.now()), pageable);
        List<ProductResponse> content = toResponses(result.getContent());
        return new PageResponse<>(content, result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages());
    }

    private static Sort parseSort(String sort) {
        String property = "name";
        Sort.Direction direction = Sort.Direction.ASC;
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            property = parts[0].trim();
            if (parts.length > 1) {
                direction = "desc".equalsIgnoreCase(parts[1].trim()) ? Sort.Direction.DESC : Sort.Direction.ASC;
            }
        }
        if (!SORTABLE.contains(property)) {
            throw new BadRequestException("VALIDATION_ERROR", "Unsupported sort field",
                    List.of(new ApiError.FieldIssue("sort", "must be one of " + new java.util.TreeSet<>(SORTABLE))));
        }
        // The id tiebreaker keeps paging stable when the sort field has duplicates.
        return Sort.by(new Sort.Order(direction, property), Sort.Order.asc("id"));
    }

    private Category findCategory(String categoryId) {
        return categories.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND", "Category not found"));
    }

    static BigDecimal validatePrice(PriceDto price) {
        BigDecimal amount = price.amount();
        boolean badAmount = amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2;
        if (badAmount || !CURRENCY.equals(price.currency())) {
            throw new BadRequestException("INVALID_PRICE",
                    "Price must be positive with at most 2 decimals and currency " + CURRENCY,
                    List.of(new ApiError.FieldIssue("price", "invalid amount or currency")));
        }
        return amount.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static ConflictException skuTaken(String sku) {
        return new ConflictException("SKU_ALREADY_EXISTS", "A product with SKU " + sku + " already exists");
    }

    private List<ProductResponse> toResponses(List<Product> list) {
        Map<String, ProductPrice> current = new HashMap<>();
        if (!list.isEmpty()) {
            prices.findCurrent(list.stream().map(Product::getId).toList(), Instant.now())
                    .forEach(p -> current.put(p.getProduct().getId(), p));
        }
        return list.stream().map(p -> {
            ProductPrice price = current.get(p.getId());
            PriceDto dto = price == null ? null : new PriceDto(price.getAmount(), price.getCurrency());
            return new ProductResponse(p.getId(), p.getSku(), p.getName(), p.getDescription(),
                    p.getCategory().getId(), p.getCategory().getName(), p.isActive(), dto, p.getCreatedAt(),
                    p.getUpdatedAt());
        }).toList();
    }
}
