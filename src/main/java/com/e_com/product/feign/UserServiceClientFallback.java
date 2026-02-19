package com.e_com.product.feign;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback implementation for UserServiceClient.
 * This is triggered when the user-service is unavailable or fails.
 * Returns safe default values instead of propagating errors.
 */
@Slf4j
@Component
public class UserServiceClientFallback implements UserServiceClient {

    @Override
    public UserDto getUserById(Long userId) {
        log.warn("Fallback triggered for getUserById({}). User service is unavailable. Returning fallback user.", userId);

        // Return a fallback user object with a special marker ID (-1) to indicate fallback was triggered
        UserDto fallbackUser = new UserDto();
        fallbackUser.setId(-1L); // Special marker to indicate fallback
        fallbackUser.setName("Fallback User");
        fallbackUser.setEmail("fallback@system.internal");

        return fallbackUser;
    }
}
