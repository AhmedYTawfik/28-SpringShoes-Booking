package com.team28.booking.invoice.service;

import com.team28.booking.invoice.cache.CacheInvalidator;
import com.team28.booking.invoice.exception.ResourceNotFoundException;
import com.team28.booking.invoice.model.Discount;
import com.team28.booking.invoice.repository.DiscountRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DiscountService {

    private final DiscountRepository discountRepository;
    private final CacheInvalidator cacheInvalidator;

    public DiscountService(DiscountRepository discountRepository, CacheInvalidator cacheInvalidator) {
        this.discountRepository = discountRepository;
        this.cacheInvalidator = cacheInvalidator;
    }

    public Discount createDiscount(Discount discount) {
        Discount saved = discountRepository.save(discount);
        cacheInvalidator.wildcardDelete("invoice-service::discount::*");
        return saved;
    }

    /** CRUD GET-by-ID — 15 min TTL (§4.4.2). */
    @Cacheable(cacheNames = "invoice-service::discount", key = "#id")
    public Discount getDiscountById(Long id) {
        return discountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Discount not found with id: " + id));
    }

    public List<Discount> getAllDiscounts() {
        return discountRepository.findAll();
    }

    public Discount updateDiscount(Long id, Discount updatedDiscount) {
        Discount existing = findById(id);
        existing.setCode(updatedDiscount.getCode());
        existing.setDiscountType(updatedDiscount.getDiscountType());
        existing.setDiscountValue(updatedDiscount.getDiscountValue());
        existing.setMaxUses(updatedDiscount.getMaxUses());
        existing.setCurrentUses(updatedDiscount.getCurrentUses());
        existing.setExpiryDate(updatedDiscount.getExpiryDate());
        existing.setActive(updatedDiscount.getActive());
        existing.setMetadata(updatedDiscount.getMetadata());
        Discount saved = discountRepository.save(existing);
        cacheInvalidator.deleteKey("invoice-service::discount::" + id);
        return saved;
    }

    public void deleteDiscount(Long id) {
        findById(id);
        discountRepository.deleteById(id);
        cacheInvalidator.deleteKey("invoice-service::discount::" + id);
    }

    /** Non-cached DB fetch used by all write paths (§4.4.4). */
    Discount findById(Long id) {
        return discountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Discount not found with id: " + id));
    }
}
