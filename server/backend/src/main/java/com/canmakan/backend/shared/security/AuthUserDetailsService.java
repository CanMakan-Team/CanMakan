package com.canmakan.backend.shared.security;

import com.canmakan.backend.user.AuthenticationAccountView;
import com.canmakan.backend.user.UserAccountRepository;
import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads the current database account and role for Spring Security authentication. */
@Service
public class AuthUserDetailsService implements UserDetailsService {

    private static final String ACCOUNT_NOT_FOUND = "Account cannot be authenticated";

    private final UserAccountRepository userAccountRepository;

    public AuthUserDetailsService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public AuthUserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        String normalizedEmail = normalizeEmail(username);
        AuthenticationAccountView account = userAccountRepository
            .findAuthenticationAccountByEmail(normalizedEmail)
            .orElseThrow(() -> new UsernameNotFoundException(ACCOUNT_NOT_FOUND));

        return buildUserDetails(account);
    }

    public AuthUserDetails loadUserById(Long userId) throws UsernameNotFoundException {
        if (userId == null || userId <= 0) {
            throw new UsernameNotFoundException(ACCOUNT_NOT_FOUND);
        }
        AuthenticationAccountView account = userAccountRepository
            .findAuthenticationAccountById(userId)
            .orElseThrow(() -> new UsernameNotFoundException(ACCOUNT_NOT_FOUND));

        return buildUserDetails(account);
    }

    private static AuthUserDetails buildUserDetails(AuthenticationAccountView account) {
        try {
            String accountEmail = normalizeEmail(account.getEmail());
            SystemRole systemRole = SystemRole.fromDatabaseName(account.getRoleName());
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
                account.getUserId(),
                accountEmail,
                Boolean.TRUE.equals(account.getActive()),
                systemRole
            );
            return new AuthUserDetails(principal, account.getPasswordHash());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new UsernameNotFoundException(ACCOUNT_NOT_FOUND, exception);
        }
    }

    private static String normalizeEmail(String email) {
        if (email == null) {
            throw new UsernameNotFoundException(ACCOUNT_NOT_FOUND);
        }
        String normalizedEmail = email.strip().toLowerCase(Locale.ROOT);
        if (normalizedEmail.isBlank()) {
            throw new UsernameNotFoundException(ACCOUNT_NOT_FOUND);
        }
        return normalizedEmail;
    }
}
