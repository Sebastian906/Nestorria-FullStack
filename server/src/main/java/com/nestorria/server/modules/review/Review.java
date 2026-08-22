package com.nestorria.server.modules.review;

import com.nestorria.server.common.persistence.Auditable;
import com.nestorria.server.modules.properties.Property;
import com.nestorria.server.modules.user.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "reviews", 
    uniqueConstraints = @UniqueConstraint(name = "uk_review_user_property", columnNames = {"user_id", "property_id"}),
    indexes = {
    @Index(name = "idx_review_property_created", columnList = "property_id, created_at DESC"),
    @Index(name = "idx_review_user_created", columnList = "user_id, created_at DESC")
})
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Min(1)
    @Max(5)
    @Column(nullable = false)
    private int rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    @Column(name = "is_verified", nullable = false)
    private boolean isVerified = false;

    public Review(User user, Property property, int rating, String comment) {
        this.user = user;
        this.property = property;
        this.rating = rating;
        this.comment = comment;
    }
}
