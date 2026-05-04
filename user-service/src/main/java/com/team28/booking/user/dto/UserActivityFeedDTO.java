package com.team28.booking.user.dto;

import java.util.ArrayList;
import java.util.List;

import com.team28.booking.user.mongo.AuthEvent;

public record UserActivityFeedDTO(
        List<UserActivity> content,
        int page,
        int size,
        int totalElements) {

    public UserActivityFeedDTO(List<AuthEvent> authEvents, int page, int size) {
        List<UserActivity> userActivities = new ArrayList<>();

        for (AuthEvent authEvent : authEvents) {
            UserActivity userActivity = new UserActivity(authEvent.getAction(), authEvent.getTimestamp(),
                    authEvent.getDetails());
            userActivities.add(userActivity);
        }

        this(userActivities, page, size, userActivities.size());
    }
}