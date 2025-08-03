package com.e_com.product.controller;

import com.e_com.product.model.Product;
import com.e_com.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Tag(name = "Product API", description = "Product Management API")
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "Get All Products", description = "Retrieve a list of all products")
    @GetMapping()
    public ResponseEntity<List<Product>> getAllProducts() {
        List<Product> products = productService.getAllProducts();
        return ResponseEntity.ok(products);
    }

    @Operation(summary = "Get Product by ID", description = "Retrieve a product by its ID")
    @GetMapping("/{productId}")
    public ResponseEntity<Product> getProductById(@PathVariable Long productId) {
        return productService.getProductById(productId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Create Product", description = "Create a new product")
    @PostMapping()
    @PreAuthorize("hasRole('ROLE_ADMIN')") // Only users with the ADMIN role can create products
    public ResponseEntity<Product> createProduct(Product product) {
        Product createdProduct = productService.createProduct(product);
        return ResponseEntity.status(201).body(createdProduct);
    }

    @Operation(summary = "Update Product", description = "Update an existing product")
    @PutMapping("/{productId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')") // Only users with the ADMIN role can update products
    public ResponseEntity<Product> updateProduct(@PathVariable Long productId, @RequestBody Product product) {
        Product updatedProduct = productService.updateProduct(productId, product);
        return updatedProduct != null
                ? ResponseEntity.ok(updatedProduct)
                : ResponseEntity.notFound().build();
    }

    @Operation(summary = "Delete Product", description = "Delete a product by its ID")
    @DeleteMapping("/{productId}")
    @PreAuthorize("hasRole('ROLE_ADMIN')") // Only users with the ADMIN role can delete products
    public ResponseEntity<Void> deleteProduct(@PathVariable Long productId) {
        productService.deleteProduct(productId);
        return ResponseEntity.noContent().build();
    }
}
