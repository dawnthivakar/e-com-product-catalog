package com.e_com.product.service;

import com.e_com.product.event.ReviewAddedEvent;
import com.e_com.product.feign.UserServiceClient;
import com.e_com.product.model.Review;
import com.e_com.product.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final UserServiceClient userServiceClient; // Feign Client to interact with User Service
    private final KafkaTemplate<String, ReviewAddedEvent> kafkaTemplate;

    private static final String REVIEW_TOPIC = "review-added-events";

    public List<Review> getReviewsByProductId(Long productId) {
        return reviewRepository.findByProductId(productId);
    }

    public Review addReview(Review review) {
        try {
            userServiceClient.getUserById(review.getUserId());
        } catch (Exception e) {
            throw new IllegalArgumentException("User not found with ID: " + review.getUserId(), e);
        }
        review.setReviewDate(LocalDateTime.now());
        Review savedReview = reviewRepository.save(review);

        // Publish an event to Kafka after saving the review
        kafkaTemplate.send(REVIEW_TOPIC, new ReviewAddedEvent(
                savedReview.getId(),
                savedReview.getProductId(),
                savedReview.getUserId(),
                savedReview.getRating(),
                savedReview.getComment(),
                savedReview.getReviewDate()
        ));
        return savedReview;
    }
}
