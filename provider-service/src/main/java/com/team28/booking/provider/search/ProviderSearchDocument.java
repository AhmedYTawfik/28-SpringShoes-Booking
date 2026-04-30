package com.team28.booking.provider.search;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "providers")
public class ProviderSearchDocument {

    @Id
    private String id;

    @Field(type = FieldType.Text)
    private String name;

    @Field(type = FieldType.Keyword)
    private String specialty;

    @Field(type = FieldType.Keyword)
    private String pricingTier;

    @Field(type = FieldType.Text)
    private String description;

    @Field(type = FieldType.Double)
    private Double rating;

    @Field(type = FieldType.Keyword)
    private String status;

    public ProviderSearchDocument() {}

    public ProviderSearchDocument(String id, String name, String specialty, String pricingTier,
                                  String description, Double rating, String status) {
        this.id = id;
        this.name = name;
        this.specialty = specialty;
        this.pricingTier = pricingTier;
        this.description = description;
        this.rating = rating;
        this.status = status;
    }

    public String getId()           { return id; }
    public String getName()         { return name; }
    public String getSpecialty()    { return specialty; }
    public String getPricingTier()  { return pricingTier; }
    public String getDescription()  { return description; }
    public Double getRating()       { return rating; }
    public String getStatus()       { return status; }

    public void setId(String id)                    { this.id = id; }
    public void setName(String name)                { this.name = name; }
    public void setSpecialty(String specialty)      { this.specialty = specialty; }
    public void setPricingTier(String pricingTier)  { this.pricingTier = pricingTier; }
    public void setDescription(String description)  { this.description = description; }
    public void setRating(Double rating)            { this.rating = rating; }
    public void setStatus(String status)            { this.status = status; }
}
