package com.scheduly.service;

import com.scheduly.dto.LoginRequest;
import com.scheduly.dto.RegisterBusinessRequest;
import com.scheduly.dto.RefreshTokenRequest;
import com.scheduly.dto.TokenResponse;
import com.scheduly.entity.Business;
import com.scheduly.entity.Role;
import com.scheduly.entity.User;
import com.scheduly.entity.RefreshToken;
import com.scheduly.exception.ConflictException;
import com.scheduly.exception.UnauthorizedException;
import com.scheduly.repository.BusinessRepository;
import com.scheduly.repository.UserRepository;
import com.scheduly.security.CustomUserDetails;
import com.scheduly.security.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final BusinessRepository businessRepository;
    private final RefreshTokenService refreshTokenService;
    private final JwtUtils jwtUtils;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;

    @Transactional
    public TokenResponse registerBusiness(RegisterBusinessRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ConflictException("Email address is already in use");
        }

        if (businessRepository.existsBySubdomain(request.getSubdomain())) {
            throw new ConflictException("Subdomain is already taken");
        }

        // 1. Create Business
        Business business = Business.builder()
                .name(request.getBusinessName())
                .subdomain(request.getSubdomain())
                .build();
        business = businessRepository.save(business);

        // 2. Create Owner User
        User owner = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getOwnerFirstName())
                .lastName(request.getOwnerLastName())
                .role(Role.BUSINESS_OWNER)
                .business(business)
                .isEnabled(true)
                .provider("LOCAL")
                .build();
        owner = userRepository.save(owner);

        // 3. Generate Auth Tokens
        CustomUserDetails userDetails = new CustomUserDetails(owner);
        String accessToken = jwtUtils.generateToken(userDetails);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(owner);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .role(owner.getRole().name())
                .email(owner.getEmail())
                .businessId(business.getId())
                .build();
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        User user = userRepository.findByEmail(userDetails.getEmail())
                .orElseThrow(() -> new UnauthorizedException("User details not found"));

        String accessToken = jwtUtils.generateToken(userDetails);
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.getToken())
                .role(user.getRole().name())
                .email(user.getEmail())
                .businessId(userDetails.getBusinessId())
                .build();
    }

    @Transactional
    public TokenResponse refreshAccessToken(RefreshTokenRequest request) {
        String requestRefreshToken = request.getRefreshToken();

        return refreshTokenService.findByToken(requestRefreshToken)
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    CustomUserDetails userDetails = new CustomUserDetails(user);
                    String newAccessToken = jwtUtils.generateToken(userDetails);
                    // Standard JWT rotation: regenerate the refresh token to prevent reuse attacks
                    RefreshToken newRefreshToken = refreshTokenService.createRefreshToken(user);
                    
                    return TokenResponse.builder()
                            .accessToken(newAccessToken)
                            .refreshToken(newRefreshToken.getToken())
                            .role(user.getRole().name())
                            .email(user.getEmail())
                            .businessId(user.getBusiness() != null ? user.getBusiness().getId() : null)
                            .build();
                })
                .orElseThrow(() -> new UnauthorizedException("Refresh token is not in the database"));
    }
}
