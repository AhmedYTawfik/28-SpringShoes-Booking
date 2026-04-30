package com.team28.booking.user.auth.handlers;

import com.team28.booking.user.repository.UserRepository;
import jakarta.servlet.http.HttpServletResponse;

public class UserLoaderHandler extends AuthHandler {

    private final UserRepository userRepository;

    public UserLoaderHandler(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void handle(AuthContext context) {
        Long uid = context.getUidClaim();
        if (uid == null) {
            context.setErrorStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        userRepository.findById(uid).ifPresentOrElse(
                context::setAuthenticatedUser,
                () -> context.setErrorStatus(HttpServletResponse.SC_UNAUTHORIZED)
        );

        handleNext(context);
    }
}
