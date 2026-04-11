package com.team28.booking.provider.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "providers")
public class Provider {
    //columns
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private String phone;

    @Column(nullable = false)
    private String specialty;

    //TODO:i think we may need to put it in a seperate file later
    public enum ProviderStatus {
        AVAILABLE,
        BUSY,
        OFFLINE
    }
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProviderStatus status;

    @Column(nullable = false)
    private Double rating;

    @Column(nullable = false)
    private Integer totalRatings;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> serviceDetails;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    //one provider <-> many certificates
    @OneToMany(mappedBy = "provider", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonManagedReference
    private List<ProviderCertification> providerCertifications = new ArrayList<>();

    public Provider() {
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        //just assumed taht the default would be available if null
        if (status == null) {
            status = ProviderStatus.AVAILABLE;
        }
        if (rating == null) {
            rating = 0.0;
        }
        if (totalRatings == null) {
            totalRatings = 0;
        }
    }

    //relation methods
    public void addCertification(ProviderCertification certification) {
        providerCertifications.add(certification);
        certification.setProvider(this);
    }

    public void removeCertification(ProviderCertification certification) {
        providerCertifications.remove(certification);
        certification.setProvider(null);
    }

    //setters
    public void setId(Long id) {
        this.id = id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public void setStatus(ProviderStatus status) {
        this.status = status;
    }

    public void setRating(Double rating) {
        this.rating = rating;
    }

    public void setTotalRatings(Integer totalRatings) {
        this.totalRatings = totalRatings;
    }

    public void setServiceDetails(Map<String, Object> serviceDetails) {
        this.serviceDetails = serviceDetails;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public void setProviderCertifications(List<ProviderCertification> providerCertifications) {
        this.providerCertifications = providerCertifications;
    }

    //getters
    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getSpecialty() {
        return specialty;
    }

    public ProviderStatus getStatus() {
        return status;
    }

    public Double getRating() {
        return rating;
    }

    public Integer getTotalRatings() {
        return totalRatings;
    }

    public Map<String, Object> getServiceDetails() {
        return serviceDetails;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public List<ProviderCertification> getProviderCertifications() {
        return providerCertifications;
    }
}
