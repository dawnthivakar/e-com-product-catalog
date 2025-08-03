package com.e_com.product.model;

import jakarta.persistence.Id;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "reviews")
@Data
public class Review {

    @Id
    private String id;
    private Long productId;
    private Long userId;
    private String comment;
    private Integer rating;
    private LocalDateTime reviewDate;
}
