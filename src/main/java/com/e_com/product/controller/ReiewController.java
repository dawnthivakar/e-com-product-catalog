package com.e_com.product.controller;

import com.e_com.product.model.Review;
import com.e_com.product.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reviews")
@RequiredArgsConstructor
@Tag(name = "Review API", description = "Review Management API")
public class ReiewController {

    private final ReviewService reviewService;

    @Operation(summary = "Get reviews by product ID", description = "Retrieve all reviews for a specific product")
    @GetMapping("/product/{productId}")
    public ResponseEntity<List<Review>> getReviewsByProductId(Long productId) {
        List<Review> reviews = reviewService.getReviewsByProductId(productId);
        return ResponseEntity.ok(reviews);
    }

    @Operation(summary = "Add a new review",
            description = "Create a new review for a product",
            security = @SecurityRequirement(name = "bearerAuth"))
    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Review> addReview(Review review) {
        Review createdReview = reviewService.addReview(review);
        return ResponseEntity.ok(createdReview);
    }
}
