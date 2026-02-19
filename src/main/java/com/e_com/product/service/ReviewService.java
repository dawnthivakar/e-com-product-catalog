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
        UserServiceClient.UserDto user;
        try {
            user = userServiceClient.getUserById(review.getUserId());

            // Check if fallback was triggered (user service is down)
            if (user.getId() != null && user.getId() == -1L) {
                throw new IllegalStateException(
                        "User verification service is temporarily unavailable. " +
                        "Unable to process your review at this time. Please try again later."
                );
            }

            // Check if user doesn't exist (null ID or doesn't match)
            if (user.getId() == null) {
                throw new IllegalArgumentException(
                        "You are not registered with us. Please register to add a review."
                );
            }

            // Verify the user ID matches
            if (!user.getId().equals(review.getUserId())) {
                throw new IllegalArgumentException(
                        "User verification failed. The user ID does not match our records."
                );
            }

        } catch (IllegalStateException | IllegalArgumentException e) {
            // Re-throw our custom exceptions as-is
            throw e;
        } catch (Exception e) {
            // Handle unexpected errors (network issues, timeouts, etc.)
            throw new IllegalStateException(
                    "Unable to verify user registration due to a technical error. Please try again later.", e
            );
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
