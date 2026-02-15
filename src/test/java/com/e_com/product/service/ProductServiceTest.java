package com.e_com.product.service;

import com.e_com.product.model.Product;
import com.e_com.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    private Product testProduct;

    @BeforeEach
    void setUp() {
        testProduct = new Product(1L, "Laptop", "Powerful laptop", 1200.0, "Electronics");
    }

    @Test
    @DisplayName("Test getProductById returns product when found")
    void testGetProductByIdReturnsProductWhenFound() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(testProduct));

        Optional<Product> foundProduct = productService.getProductById(1L);

        assertTrue(foundProduct.isPresent(), "Product should be found");
        assertEquals(testProduct, foundProduct.get(), "Found product should match the test product");
        verify(productRepository, times(1)).findById(1L);
    }

    @ParameterizedTest
    @ValueSource(longs = {2L, 3L, 4L})
    @DisplayName("Test getProductById returns empty when not found")
    void testGetProductByIdReturnsEmptyWhenNotFound(long id) {
        when(productRepository.findById(id)).thenReturn(Optional.empty());

        Optional<Product> foundProduct = productService.getProductById(id);

        assertTrue(foundProduct.isEmpty(), "Product should not be found");
        verify(productRepository, times(1)).findById(id);
    }

    @ParameterizedTest
    @MethodSource("provideProducts")
    @DisplayName("Test createProduct saves and returns product")
    void testCreateProductSavesAndReturnsProduct(Product product) {
        when(productRepository.save(product)).thenReturn(product);

        Product createdProduct = productService.createProduct(product);

        assertNotNull(createdProduct);
        assertEquals(product, createdProduct, "Created product should match the test product");
        verify(productRepository, times(1)).save(product);
    }

    private static Stream<Product> provideProducts() {
        return Stream.of(
            new Product(1L, "Laptop", "Powerful laptop", 1200.0, "Electronics"),
            new Product(2L, "Smartphone", "Latest smartphone", 800.0, "Electronics"),
            new Product(3L, "Book", "Interesting book", 600.0, "Books")
        );
    }

}