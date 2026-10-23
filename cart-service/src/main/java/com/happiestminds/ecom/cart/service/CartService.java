package com.happiestminds.ecom.cart.service;

import com.happiestminds.ecom.cart.api.dto.AddItemRequest;
import com.happiestminds.ecom.cart.api.dto.CartItemResponse;
import com.happiestminds.ecom.cart.api.dto.CartResponse;
import com.happiestminds.ecom.cart.api.dto.CheckoutSummaryResponse;
import com.happiestminds.ecom.cart.api.dto.SummaryItem;
import com.happiestminds.ecom.cart.client.CatalogClient;
import com.happiestminds.ecom.cart.domain.CartItem;
import com.happiestminds.ecom.cart.domain.CartStatus;
import com.happiestminds.ecom.cart.domain.DiscountRule;
import com.happiestminds.ecom.cart.domain.ShoppingCart;
import com.happiestminds.ecom.cart.repository.DiscountRuleRepository;
import com.happiestminds.ecom.cart.repository.ShoppingCartRepository;
import com.happiestminds.ecom.cart.service.exception.BadRequestException;
import com.happiestminds.ecom.cart.service.exception.NotFoundException;
import com.happiestminds.ecom.cart.service.exception.UnprocessableException;
import com.happiestminds.ecom.cart.service.pricing.PricingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

// Catalog calls happen outside any transaction; only local reads and writes use the TransactionTemplate.
@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);
    static final String CURRENCY = "INR";
    private static final int MIN_QUANTITY = 1;
    private static final int MAX_QUANTITY = 100;

    private record ItemSnapshot(String productId) {
    }

    private record Snapshot(String cartId, List<ItemSnapshot> items) {
    }

    private final ShoppingCartRepository carts;
    private final DiscountRuleRepository discounts;
    private final PricingService pricing;
    private final CatalogClient catalog;
    private final TransactionTemplate tx;

    public CartService(ShoppingCartRepository carts, DiscountRuleRepository discounts, PricingService pricing,
                       CatalogClient catalog, PlatformTransactionManager txManager) {
        this.carts = carts;
        this.discounts = discounts;
        this.pricing = pricing;
        this.catalog = catalog;
        this.tx = new TransactionTemplate(txManager);
    }

    public CartResponse current(String customerId) {
        return tx.execute(status -> toResponse(getOrCreate(customerId)));
    }

    public CartResponse addItem(String customerId, AddItemRequest request) {
        int quantity = requireQuantity(request.quantity());
        CatalogClient.ProductInfo product = fetchActiveProduct(request.productId());
        BigDecimal price = product.price().amount().setScale(2, RoundingMode.HALF_UP);
        return tx.execute(status -> {
            ShoppingCart cart = getOrCreate(customerId);
            Optional<CartItem> existing = cart.getItems().stream()
                    .filter(i -> i.getProductId().equals(product.id())).findFirst();
            if (existing.isPresent()) {
                CartItem item = existing.get();
                item.setQuantity(requireQuantity(item.getQuantity() + quantity));
                item.refreshProduct(product.name(), price);
            } else {
                cart.addItem(new CartItem(cart, product.id(), product.sku(), product.name(), quantity, price));
            }
            carts.flush();
            return toResponse(cart);
        });
    }

    public CartResponse updateItem(String customerId, String itemId, Integer quantity) {
        int newQuantity = requireQuantity(quantity);
        return tx.execute(status -> {
            ShoppingCart cart = getOrCreate(customerId);
            findItem(cart, itemId).setQuantity(newQuantity);
            carts.flush();
            return toResponse(cart);
        });
    }

    public void removeItem(String customerId, String itemId) {
        tx.executeWithoutResult(status -> {
            ShoppingCart cart = getOrCreate(customerId);
            cart.removeItem(findItem(cart, itemId));
            carts.flush();
        });
    }

    public CartResponse applyDiscount(String customerId, String code) {
        String normalised = code.trim().toUpperCase(Locale.ROOT);
        return tx.execute(status -> {
            ShoppingCart cart = getOrCreate(customerId);
            DiscountRule rule = discounts.findByCode(normalised)
                    .orElseThrow(() -> new NotFoundException("DISCOUNT_NOT_FOUND", "Discount code not found"));
            BigDecimal subtotal = pricing.subtotal(lines(cart));
            if (!pricing.applicable(rule, subtotal, Instant.now())) {
                throw new UnprocessableException("DISCOUNT_NOT_APPLICABLE",
                        "Discount is inactive, expired or the cart is below the minimum order total");
            }
            cart.setDiscountCode(rule.getCode());
            return toResponse(cart);
        });
    }

    public CheckoutSummaryResponse checkoutSummary(String customerId) {
        Snapshot snapshot = tx.execute(status -> {
            ShoppingCart cart = getOrCreate(customerId);
            return new Snapshot(cart.getId(),
                    cart.getItems().stream().map(i -> new ItemSnapshot(i.getProductId())).toList());
        });
        if (snapshot.items().isEmpty()) {
            throw new UnprocessableException("EMPTY_CART", "Cart is empty");
        }
        // One Catalog read per product, outside any transaction; client-side prices are never trusted.
        Map<String, CatalogClient.ProductInfo> fresh = new LinkedHashMap<>();
        for (ItemSnapshot item : snapshot.items()) {
            fresh.put(item.productId(), fetchActiveProduct(item.productId()));
        }
        return tx.execute(status -> {
            ShoppingCart cart = carts.findById(snapshot.cartId()).orElseThrow();
            cart.getItems().forEach(i -> {
                CatalogClient.ProductInfo p = fresh.get(i.getProductId());
                if (p != null) {
                    i.refreshProduct(p.name(), p.price().amount().setScale(2, RoundingMode.HALF_UP));
                }
            });
            DiscountRule rule = ruleFor(cart);
            PricingService.Summary summary = pricing.summarise(lines(cart), rule, Instant.now());
            List<SummaryItem> items = cart.getItems().stream()
                    .map(i -> new SummaryItem(i.getProductId(), i.getSku(), i.getProductName(), i.getQuantity(),
                            i.getUnitPrice(), lineTotal(i)))
                    .toList();
            return new CheckoutSummaryResponse(cart.getId(), cart.getCustomerId(), items, summary.subtotal(),
                    summary.discountCode(), summary.discountAmount(), summary.total(), CURRENCY);
        });
    }

    public void markCheckedOut(String cartId) {
        tx.executeWithoutResult(status -> {
            ShoppingCart cart = carts.findById(cartId)
                    .orElseThrow(() -> new NotFoundException("CART_NOT_FOUND", "Cart not found"));
            if (cart.getStatus() == CartStatus.ACTIVE) {
                cart.checkOut();
                log.info("Cart {} marked CHECKED_OUT", cartId);
            }
        });
    }

    private ShoppingCart getOrCreate(String customerId) {
        return carts.findFirstByCustomerIdAndStatus(customerId, CartStatus.ACTIVE)
                .orElseGet(() -> carts.save(new ShoppingCart(customerId)));
    }

    private CatalogClient.ProductInfo fetchActiveProduct(String productId) {
        CatalogClient.ProductInfo product = catalog.getProduct(productId);
        if (!product.active() || product.price() == null) {
            throw new NotFoundException("PRODUCT_NOT_FOUND", "Product not found or not active");
        }
        return product;
    }

    private static CartItem findItem(ShoppingCart cart, String itemId) {
        return cart.getItems().stream().filter(i -> itemId.equals(i.getId())).findFirst()
                .orElseThrow(() -> new NotFoundException("CART_ITEM_NOT_FOUND", "Cart item not found"));
    }

    private static int requireQuantity(Integer quantity) {
        if (quantity == null || quantity < MIN_QUANTITY || quantity > MAX_QUANTITY) {
            throw new BadRequestException("INVALID_QUANTITY",
                    "Quantity must be between " + MIN_QUANTITY + " and " + MAX_QUANTITY);
        }
        return quantity;
    }

    private DiscountRule ruleFor(ShoppingCart cart) {
        return cart.getDiscountCode() == null ? null : discounts.findByCode(cart.getDiscountCode()).orElse(null);
    }

    private static List<PricingService.Line> lines(ShoppingCart cart) {
        return cart.getItems().stream()
                .map(i -> new PricingService.Line(i.getUnitPrice(), i.getQuantity()))
                .toList();
    }

    private static BigDecimal lineTotal(CartItem i) {
        return i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())).setScale(2, RoundingMode.HALF_UP);
    }

    private CartResponse toResponse(ShoppingCart cart) {
        PricingService.Summary summary = pricing.summarise(lines(cart), ruleFor(cart), Instant.now());
        List<CartItemResponse> items = cart.getItems().stream()
                .map(i -> new CartItemResponse(i.getId(), i.getProductId(), i.getSku(), i.getProductName(),
                        i.getQuantity(), i.getUnitPrice(), lineTotal(i)))
                .toList();
        return new CartResponse(cart.getId(), cart.getCustomerId(), items, summary.subtotal(),
                summary.discountCode(), summary.discountAmount(), summary.total(), CURRENCY);
    }
}
