package com.canmakan.backend.shared.security;

import com.canmakan.backend.user.AuthenticationAccountView;
import com.canmakan.backend.user.repository.UserAccountRepository;
import java.util.Locale;
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
        String normalizedEmail = normalizeEmail(username);
        AuthenticationAccountView account = userAccountRepository
            .findAuthenticationAccountByEmail(normalizedEmail)
            .orElseThrow(() -> new UsernameNotFoundException(ACCOUNT_NOT_FOUND));

        return buildUserDetails(account);
    }

    public AuthUserDetails loadUserById(Long userId) throws UsernameNotFoundException {
        // Always go through the same repository lookup, even for a null or non-positive id,
        // instead of short-circuiting before the database call. A malformed id simply matches
        // no row and falls through to the same exception as a genuine "not found" case, so the
        // response timing and outcome cannot be used to distinguish input formats from real ids.
        AuthenticationAccountView account = userAccountRepository
            .findAuthenticationAccountById(userId)
            .orElseThrow(() -> new UsernameNotFoundException(ACCOUNT_NOT_FOUND));

        return buildUserDetails(account);
    }

    private static AuthUserDetails buildUserDetails(AuthenticationAccountView account) {
        try {
            String accountEmail = normalizeEmail(account.getEmail());
            if (accountEmail.isBlank()) {
                throw new IllegalStateException("Account record is missing an email");
            }
            SystemRole systemRole = SystemRole.fromDatabaseName(account.getRoleName());
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                account.getUserId(),
                accountEmail,
                Boolean.TRUE.equals(account.getActive()),
                systemRole
            );
            return new AuthUserDetails(principal, account.getPasswordHash());
        } catch (IllegalArgumentException | IllegalStateException | NullPointerException exception) {
            throw new UsernameNotFoundException(ACCOUNT_NOT_FOUND, exception);
        }
    }

    // Normalizes without rejecting a null/blank input so callers funnel every case, malformed or
    // merely absent, through the same repository lookup and orElseThrow below (see loadUserById),
    // instead of a fast-fail branch that would let response timing reveal whether an account exists.
    private static String normalizeEmail(String email) {
        return email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
    }
}
