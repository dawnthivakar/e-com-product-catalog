package com.e_com.product.repository;

import com.e_com.product.model.Review;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface ReviewRepository extends MongoRepository<Review, String> {

    // Method to find reviews by product ID
    List<Review> findByProductId(Long productId);

    // Method to find reviews by user ID
    List<Review> findByUserId(Long userId);
}
