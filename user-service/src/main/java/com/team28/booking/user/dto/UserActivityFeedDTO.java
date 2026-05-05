package com.team28.booking.user.dto;

import java.util.ArrayList;
import java.util.List;

import com.team28.booking.user.mongo.AuthEvent;

public record UserActivityFeedDTO(
        List<UserActivity> content,
        int page,
        int size,
        int totalElements) {

    public static UserActivityFeedDTO build(List<AuthEvent> authEvents, int page, int size, int totalElements) {
        List<UserActivity> userActivities = new ArrayList<>();

        for (AuthEvent authEvent : authEvents) {
            UserActivity userActivity = new UserActivity(authEvent.getAction(), authEvent.getTimestamp(),
                    authEvent.getDetails());
            userActivities.add(userActivity);
        }

        return new UserActivityFeedDTO(userActivities, page, size, totalElements);
    }
}