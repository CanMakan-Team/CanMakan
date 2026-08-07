package com.canmakan.backend.auth;

import com.canmakan.backend.user.UserAccount;
import com.canmakan.backend.user.UserAccountRepository;
import java.sql.SQLException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates standalone user accounts through the public registration flow. */
@Service
@RequiredArgsConstructor
public class RegistrationService {

    static final String PUBLIC_REGISTRATION_ROLE = "USER";

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;

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
