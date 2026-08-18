package com.canmakan.backend.family.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.canmakan.backend.family.exception.InvitationEmailMismatchException;
import com.canmakan.backend.family.model.FamilyInvitation;
import com.canmakan.backend.family.model.InvitationStatus;
import com.canmakan.backend.family.repository.FamilyInvitationRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("InvitationRegistrationGuard")
class InvitationRegistrationGuardTest {

    @Mock
    private FamilyInvitationRepository familyInvitationRepository;

    private InvitationRegistrationGuard guard;

    @BeforeEach
    void setUp() {
        guard = new InvitationRegistrationGuard(familyInvitationRepository);
    }

    @Test
    void matchingPendingEmailIsAllowed() {
        when(familyInvitationRepository.findByInvitationToken("tok"))
            .thenReturn(Optional.of(pendingInvite("jamie@example.com")));

        assertDoesNotThrow(() ->
            guard.requireEmailMatchesPendingInvite("tok", "Jamie@Example.com"));
    }

    @Test
    void differentEmailIsRejected() {
        when(familyInvitationRepository.findByInvitationToken("tok"))
            .thenReturn(Optional.of(pendingInvite("jamie@example.com")));

        InvitationEmailMismatchException exception = assertThrows(
            InvitationEmailMismatchException.class,
            () -> guard.requireEmailMatchesPendingInvite("tok", "other@example.com")
        );
        assertEquals(InvitationRegistrationGuard.MISMATCH_MESSAGE, exception.getMessage());
    }

    @Test
    void missingTokenDoesNotBlockOpenRegistration() {
        assertDoesNotThrow(() ->
            guard.requireEmailMatchesPendingInvite(null, "other@example.com"));
    }

    private static FamilyInvitation pendingInvite(String email) {
        FamilyInvitation invitation = new FamilyInvitation();
        invitation.setInvitedEmail(email);
        invitation.setInvitationToken("tok");
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        return invitation;
    }
}
