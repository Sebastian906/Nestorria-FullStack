package com.nestorria.server.modules.user;

import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.nestorria.server.common.persistence.Auditable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User extends Auditable {

    @Id
    @Setter(AccessLevel.NONE)
    @Column(length = 50)
    private String id;

    @NotBlank
    @Column(nullable = false)
    private String username;

    @NotBlank
    @Email
    @Column(nullable = false, unique = true)
    private String email;

    @NotBlank
    @Column(nullable = false)
    private String image;

    @Column(nullable = false)
    private UserRole role = UserRole.USER;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "recent_searched_cities", columnDefinition = "text[]")
    private List<String> recentSearchedCities = new ArrayList<>();

    public User(String id, String username, String email, String image) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.image = image;
        this.role = UserRole.USER;
    }
}
