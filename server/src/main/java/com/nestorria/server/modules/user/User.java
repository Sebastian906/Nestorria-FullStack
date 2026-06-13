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

@Entity
@Table(name = "users")
public class User extends Auditable {

    @Id
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

    protected User() {
        // requerido por JPA
    }

    public User(String id, String username, String email, String image) {
        this.id = id;
        this.username = username;
        this.email = email;
        this.image = image;
        this.role = UserRole.USER;
    }

    public String getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public List<String> getRecentSearchedCities() {
        return recentSearchedCities;
    }

    public void setRecentSearchedCities(List<String> recentSearchedCities) {
        this.recentSearchedCities = recentSearchedCities;
    }
}
