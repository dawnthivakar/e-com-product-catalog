package com.e_com.product.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewAddedEvent {

    private String reviewId;
    private Long productId;
    private Long userId;
    private Integer rating;
    private String comment;
    private LocalDateTime reviewDate;
}
