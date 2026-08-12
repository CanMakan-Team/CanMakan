package com.canmakan.backend.auth;

import com.canmakan.backend.auth.dto.AuthResponse;
import com.canmakan.backend.auth.dto.AuthenticationResult;
import com.canmakan.backend.auth.dto.CurrentUserResponse;
import com.canmakan.backend.auth.dto.LoginRequest;
import com.canmakan.backend.auth.dto.RegistrationRequest;
import com.canmakan.backend.auth.dto.RegistrationResponse;
import com.canmakan.backend.auth.exception.AuthenticationFailedException;
import com.canmakan.backend.auth.exception.DuplicateEmailException;
import com.canmakan.backend.auth.exception.RefreshAuthenticationException;
import com.canmakan.backend.auth.exception.RegistrationFailedException;
import com.canmakan.backend.auth.model.IssuedRefreshToken;
import com.canmakan.backend.auth.model.RefreshTokenRotation;
import com.canmakan.backend.shared.security.AuthUserDetails;
import com.canmakan.backend.shared.security.JwtService;
import com.canmakan.backend.user.UserAccount;
import com.canmakan.backend.user.UserAccountRepository;

import lombok.RequiredArgsConstructor;

import java.sql.SQLException;

import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Authenticates current accounts and issues short-lived access tokens. 
 * 
 * @author YangMaowei
 * @author Amelia
*/
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String TOKEN_TYPE = "Bearer";
    static final String PUBLIC_REGISTRATION_ROLE = "USER";

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    // User login
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

    // User refresh token
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

    // User logout
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

    // User registration
    @Transactional
    public RegistrationResponse register(RegistrationRequest request) {
        String normalizedEmail = request.email();

        try {
            if (userAccountRepository.existsByEmail(normalizedEmail)) {
                throw new DuplicateEmailException();
            }

            Long userRoleId = userAccountRepository.findRoleIdByName(PUBLIC_REGISTRATION_ROLE)
                .orElseThrow(RegistrationFailedException::new);

            UserAccount account = new UserAccount();
            account.setRoleId(userRoleId);
            account.setEmail(normalizedEmail);
            account.setPasswordHash(passwordEncoder.encode(request.password()));
            account.setActive(true);

            UserAccount savedAccount = userAccountRepository.saveAndFlush(account);

            return new RegistrationResponse(
                savedAccount.getId(),
                savedAccount.getEmail(),
                savedAccount.isActive()
            );
        } catch (DuplicateEmailException exception) {
            throw exception;
        } catch (DataIntegrityViolationException exception) {
            if (isMysqlDuplicateKey(exception)) {
                throw new DuplicateEmailException();
            }
            throw new RegistrationFailedException(exception);
        } catch (DataAccessException exception) {
            throw new RegistrationFailedException(exception);
        }
    }

    private static boolean isMysqlDuplicateKey(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && sqlException.getErrorCode() == 1062) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
    
}
