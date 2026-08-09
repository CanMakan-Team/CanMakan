package com.canmakan.backend.auth;

import com.canmakan.backend.dietaryprofile.DietaryProfileRepository;
import com.canmakan.backend.user.UserAccount;
import com.canmakan.backend.user.UserAccountRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Email/password login against {@code users} (pre-JWT / UC19).
 * Maps platform roles to web session role strings for portal access.
 * 
 * @author Amelia
 * @author YangMaowei
 */
@Service
@RequiredArgsConstructor
public class LoginService {

    static final String PLATFORM_USER = "USER";
    static final String PLATFORM_ADMIN = "ADMIN";

    private final UserAccountRepository userAccountRepository;
    private final DietaryProfileRepository dietaryProfileRepository;
    private final PasswordEncoder passwordEncoder;

    // Login a user
    // Transactional read only to ensure data consistency
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {

        // 1. Find the user by email
        UserAccount account = userAccountRepository.findByEmail(request.email())
            .orElseThrow(InvalidCredentialsException::new);

        // 2. Check if the user is active
        if (!account.isActive()) {
            throw new InvalidCredentialsException();
        }

        // 3. Check if the password is correct
        if (!passwordEncoder.matches(request.password(), account.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        // 4. Get the platform role
        String platformRole = userAccountRepository.findRoleNameById(account.getRoleId())
            .orElse(PLATFORM_USER);

        // 5. Return the login response
        return new LoginResponse(
            account.getId(),
            resolveDisplayName(account),
            mapWebRoles(platformRole),
            false
        );
    }

    // --- Helper methods ---

    // Resolve the display name for the user
    private String resolveDisplayName(UserAccount account) {
        return dietaryProfileRepository.findByLinkedUser_Id(account.getId())
            .map(profile -> profile.getProfileName())
            .filter(name -> name != null && !name.isBlank())
            .orElseGet(() -> emailLocalPart(account.getEmail()));
    }

    // Extract the local part of the email address
    private static String emailLocalPart(String email) {
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    /**
     * Interim mapping until UC19 JWT claims. USER gets family-portal access so
     * registrants can reach UC8 create-circle; ADMIN maps to system portal.
     */
    static List<String> mapWebRoles(String platformRole) {
        if (PLATFORM_ADMIN.equalsIgnoreCase(platformRole)) {
            return List.of("ROLE_SYSTEM_ADMIN");
        }
        return List.of("ROLE_APP_USER", "ROLE_FAMILY_ADMIN");
    }
}
