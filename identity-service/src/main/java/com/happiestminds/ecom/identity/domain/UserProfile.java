package com.happiestminds.ecom.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(length = 20)
    private String phone;

    @Column(length = 200)
    private String addressLine1;

    @Column(length = 100)
    private String city;

    @Column(length = 20)
    private String postcode;

    @Column(nullable = false)
    private Instant updatedAt;

    protected UserProfile() {
    }

    public UserProfile(User user, String fullName) {
        this.user = user;
        this.fullName = fullName;
    }

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public void update(String fullName, String phone, String addressLine1, String city, String postcode) {
        this.fullName = fullName;
        this.phone = phone;
        this.addressLine1 = addressLine1;
        this.city = city;
        this.postcode = postcode;
    }

    public String getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getFullName() {
        return fullName;
    }

    public String getPhone() {
        return phone;
    }

    public String getAddressLine1() {
        return addressLine1;
    }

    public String getCity() {
        return city;
    }

    public String getPostcode() {
        return postcode;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
