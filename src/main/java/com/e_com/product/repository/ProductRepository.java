package com.e_com.product.repository;

import com.e_com.product.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
    // Additional query methods can be defined here if needed
}
