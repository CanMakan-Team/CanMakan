package com.canmakan.backend.auth;

import com.canmakan.backend.shared.security.AuthUserDetails;
import com.canmakan.backend.shared.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authenticates current accounts and issues short-lived access tokens. */
@Service
@RequiredArgsConstructor
public class AuthenticationService {

    private static final String TOKEN_TYPE = "Bearer";

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public AuthenticationResult login(LoginRequest request) {
        AuthUserDetails userDetails;
        try {
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );
            if (!(authentication.getPrincipal() instanceof AuthUserDetails authenticatedUser)) {
                throw new BadCredentialsException("Unsupported authenticated principal");
            }
            userDetails = authenticatedUser;
        } catch (AuthenticationServiceException exception) {
            throw exception;
        } catch (AuthenticationException exception) {
            throw new AuthenticationFailedException();
        }

        String accessToken = jwtService.issueAccessToken(userDetails.getUserId());
        IssuedRefreshToken refreshToken = refreshTokenService.createSession(userDetails.getUserId());
        return authenticationResult(userDetails, accessToken, refreshToken);
    }

    @Transactional(noRollbackFor = RefreshAuthenticationException.class)
    public AuthenticationResult refresh(String rawRefreshToken) {
        RefreshTokenRotation rotation = refreshTokenService.rotate(rawRefreshToken);
        String accessToken = jwtService.issueAccessToken(rotation.userDetails().getUserId());
        return authenticationResult(
            rotation.userDetails(),
            accessToken,
            rotation.issuedRefreshToken()
        );
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revokeSession(rawRefreshToken);
    }

    private AuthenticationResult authenticationResult(
            AuthUserDetails userDetails,
            String accessToken,
            IssuedRefreshToken refreshToken) {
        AuthResponse response = new AuthResponse(
            accessToken,
            TOKEN_TYPE,
            jwtService.accessTokenTtlSeconds(),
            CurrentUserResponse.from(userDetails)
        );
        return new AuthenticationResult(response, refreshToken);
    }
}
