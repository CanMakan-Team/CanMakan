package com.canmakan.backend.shared.security;

import com.canmakan.backend.user.repository.AuthenticationAccountView;
import com.canmakan.backend.user.repository.UserAccountRepository;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads the current database account and role for Spring Security authentication. */
@Service
@RequiredArgsConstructor
public class AuthUserDetailsService implements UserDetailsService {

    private static final String ACCOUNT_NOT_FOUND = "Account cannot be authenticated";

    private final UserAccountRepository userAccountRepository;

    @Override
    public AuthUserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userAccountRepository
            .findAuthenticationAccountByEmail(normalizeEmail(username))
            .flatMap(AuthUserDetailsService::toUserDetails)
            .orElseThrow(AuthUserDetailsService::accountNotFound);
    }

    public AuthUserDetails loadUserById(Long userId) throws UsernameNotFoundException {
        // Always go through the same repository lookup, even for a null or non-positive id,
        // instead of short-circuiting before the database call. A malformed id simply matches
        // no row and falls through to the same exception as a genuine "not found" case, so the
        // response timing and outcome cannot be used to distinguish input formats from real ids.
        return userAccountRepository
            .findAuthenticationAccountById(userId)
            .flatMap(AuthUserDetailsService::toUserDetails)
            .orElseThrow(AuthUserDetailsService::accountNotFound);
    }

    private static Optional<AuthUserDetails> toUserDetails(AuthenticationAccountView account) {
        String accountEmail = normalizeEmail(account.getEmail());
        if (accountEmail.isBlank()) {
            return Optional.empty();
        }
        SystemRole systemRole;
        try {
            systemRole = SystemRole.fromDatabaseName(account.getRoleName());
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
            account.getUserId(),
            accountEmail,
            Boolean.TRUE.equals(account.getActive()),
            systemRole
        );
        return Optional.of(new AuthUserDetails(principal, account.getPasswordHash()));
    }

    private static UsernameNotFoundException accountNotFound() {
        return new UsernameNotFoundException(ACCOUNT_NOT_FOUND);
    }

    // Normalizes without rejecting a null/blank input so callers funnel every case, malformed or
    // merely absent, through the same repository lookup and orElseThrow below (see loadUserById),
    // instead of a fast-fail branch that would let response timing reveal whether an account exists.
    private static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
}
