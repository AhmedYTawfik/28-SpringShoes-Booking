package com.team28.booking.provider.model;


import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@Table(name = "provider_certifications")
public class ProviderCertification {
    //columns
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //TODO:i think we may need to put it in a seperate file later
    public enum ProviderCertificationType {
        LICENSE,
        DEGREE,
        CERTIFICATION,
        INSURANCE
    }
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProviderCertificationType type;

    @Column(nullable = false)
    private String documentUrl;

    @Column(nullable = false)
    private LocalDate expiryDate;

    @Column(nullable = false)
    private Boolean verified;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(nullable = false, updatable = false)
    private LocalDateTime uploadedAt;

    //many certificate <-> only one provider
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id", nullable = false)
    @JsonBackReference
    private Provider provider;

    public ProviderCertification() {
    }

    @PrePersist
    public void prePersist() {
        if (uploadedAt == null) {
            uploadedAt = LocalDateTime.now();
        }
        if (verified == null) {
            verified = false;
        }
    }

    //setters
    public void setId(Long id) {
        this.id = id;
    }

    public void setType(ProviderCertificationType type) {
        this.type = type;
    }

    public void setDocumentUrl(String documentUrl) {
        this.documentUrl = documentUrl;
    }

    public void setExpiryDate(LocalDate expiryDate) {
        this.expiryDate = expiryDate;
    }

    public void setVerified(Boolean verified) {
        this.verified = verified;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public void setUploadedAt(LocalDateTime uploadedAt) {
        this.uploadedAt = uploadedAt;
    }

    public void setProvider(Provider provider) {
        this.provider = provider;
    }

    //getters
    public Long getId() {
        return id;
    }

    public ProviderCertificationType getType() {
        return type;
    }

    public String getDocumentUrl() {
        return documentUrl;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public Boolean getVerified() {
        return verified;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public LocalDateTime getUploadedAt() {
        return uploadedAt;
    }

    public Provider getProvider() {
        return provider;
    }
}
