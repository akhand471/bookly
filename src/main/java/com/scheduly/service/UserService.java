package com.scheduly.service;

import com.scheduly.dto.UserResponse;
import com.scheduly.entity.User;
import com.scheduly.exception.ResourceNotFoundException;
import com.scheduly.mapper.UserMapper;
import com.scheduly.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for user profile operations.
 * Separates repository access from the controller layer, following Clean Architecture principles.
 * Controllers should ONLY communicate with Services, never directly with Repositories.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    /**
     * Fetches a user by their UUID and maps to a safe response DTO.
     * Throws 404 if the user does not exist.
     */
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User profile not found"));
        return userMapper.toResponse(user);
    }

    /**
     * Fetches the raw User entity by ID — used internally when a full entity is needed.
     */
    @Transactional(readOnly = true)
    public User findById(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }
}
