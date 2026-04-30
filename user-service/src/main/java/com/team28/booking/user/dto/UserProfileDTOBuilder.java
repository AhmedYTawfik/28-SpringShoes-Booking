package com.team28.booking.user.dto;

import java.util.List;
import java.util.Map;

public class UserProfileDTOBuilder {
    private Long userId;
    private String name;
    private String email;
    private String phone;
    private Map<String, Object> preferences;
    private List<SavedAddressDTO> savedAddresses;
    private Long totalAddresses;

    public UserProfileDTOBuilder userId(Long userId) { this.userId = userId; return this; }
    public UserProfileDTOBuilder name(String name) { this.name = name; return this; }
    public UserProfileDTOBuilder email(String email) { this.email = email; return this; }
    public UserProfileDTOBuilder phone(String phone) { this.phone = phone; return this; }
    public UserProfileDTOBuilder preferences(Map<String, Object> preferences) { this.preferences = preferences; return this; }
    public UserProfileDTOBuilder savedAddresses(List<SavedAddressDTO> savedAddresses) { this.savedAddresses = savedAddresses; return this; }
    public UserProfileDTOBuilder totalAddresses(Long totalAddresses) { this.totalAddresses = totalAddresses; return this; }

    public UserProfileDTO build() {
        return new UserProfileDTO(userId, name, email, phone, preferences, savedAddresses, totalAddresses);
    }
}
