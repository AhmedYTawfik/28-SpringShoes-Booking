package com.team28.booking.invoice.service;

import com.team28.booking.invoice.exception.ResourceNotFoundException;
import com.team28.booking.invoice.model.Discount;
import com.team28.booking.invoice.repository.DiscountRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class DiscountService {

    private final DiscountRepository discountRepository;

    public DiscountService(DiscountRepository discountRepository) {
        this.discountRepository = discountRepository;
    }

    // Create
    public Discount createDiscount(Discount discount) {
        return discountRepository.save(discount);
    }

    // Read by ID
    public Discount getDiscountById(Long id) {
        return discountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Discount not found with id: " + id));
    }

    // Read all
    public List<Discount> getAllDiscounts() {
        return discountRepository.findAll();
    }

    // Update
    public Discount updateDiscount(Long id, Discount updatedDiscount) {
        Discount existing = getDiscountById(id);
        existing.setCode(updatedDiscount.getCode());
        existing.setDiscountType(updatedDiscount.getDiscountType());
        existing.setDiscountValue(updatedDiscount.getDiscountValue());
        existing.setMaxUses(updatedDiscount.getMaxUses());
        existing.setCurrentUses(updatedDiscount.getCurrentUses());
        existing.setExpiryDate(updatedDiscount.getExpiryDate());
        existing.setActive(updatedDiscount.getActive());
        existing.setMetadata(updatedDiscount.getMetadata());
        return discountRepository.save(existing);
    }

    // Delete
    public void deleteDiscount(Long id) {
        discountRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Discount not found with id: " + id));
        discountRepository.deleteById(id);
    }
}