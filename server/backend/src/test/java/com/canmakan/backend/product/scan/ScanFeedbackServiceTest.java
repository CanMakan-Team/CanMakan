package com.canmakan.backend.product.scan;

import com.canmakan.backend.family.FamilyAuthorizationService;
import com.canmakan.backend.family.exception.FamilyForbiddenException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Kwok Heng
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UC20: ScanFeedbackService thumbs up/down submission")
class ScanFeedbackServiceTest {

    @Mock
    private ScanRepository scanRepository;

    @Mock
    private ScanFeedbackRepository scanFeedbackRepository;

    @Mock
    private FamilyAuthorizationService familyAuthorizationService;

    private ScanFeedbackService service;

    @BeforeEach
    void setUp() {
        service = new ScanFeedbackService(scanRepository, scanFeedbackRepository, familyAuthorizationService);
    }

    @Test
    @DisplayName("saves an elaborated thumbs down with resolved=false")
    void savesNegativeReportWithComment() {
        Scan scan = new Scan();
        scan.setId(19L);
        scan.setProfileId(4L);
        when(scanRepository.findById(19L)).thenReturn(Optional.of(scan));
        when(scanFeedbackRepository.save(org.mockito.ArgumentMatchers.any(ScanFeedback.class)))
                .thenAnswer(invocation -> {
                    ScanFeedback saved = invocation.getArgument(0);
                    saved.setId(3L);
                    return saved;
                });

        ScanFeedbackResponse response = service.submitFeedback(7L, 19L, false, "Wrong allergen listed");

        assertThat(response.id()).isEqualTo(3L);
        assertThat(response.scanId()).isEqualTo(19L);
        assertThat(response.isPositive()).isFalse();
        assertThat(response.userComments()).isEqualTo("Wrong allergen listed");
        assertThat(response.resolved()).isFalse();
        assertThat(response.createdAt()).isNotNull();
        verify(familyAuthorizationService).assertProfileAuthorizedForScan(7L, 4L);
    }

    @Test
    @DisplayName("saves a bare thumbs down as a null comment, not a blank string")
    void blankCommentIsSavedAsNull() {
        Scan scan = new Scan();
        scan.setId(19L);
        scan.setProfileId(4L);
        when(scanRepository.findById(19L)).thenReturn(Optional.of(scan));

        ArgumentCaptor<ScanFeedback> captor = ArgumentCaptor.forClass(ScanFeedback.class);
        when(scanFeedbackRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        service.submitFeedback(7L, 19L, false, "   ");

        assertThat(captor.getValue().getUserComments()).isNull();
        assertThat(captor.getValue().isPositive()).isFalse();
    }

    @Test
    @DisplayName("saves a thumbs up with isPositive=true and a null comment")
    void savesPositiveReport() {
        Scan scan = new Scan();
        scan.setId(19L);
        scan.setProfileId(4L);
        when(scanRepository.findById(19L)).thenReturn(Optional.of(scan));

        ArgumentCaptor<ScanFeedback> captor = ArgumentCaptor.forClass(ScanFeedback.class);
        when(scanFeedbackRepository.save(captor.capture())).thenAnswer(invocation -> invocation.getArgument(0));

        ScanFeedbackResponse response = service.submitFeedback(7L, 19L, true, null);

        assertThat(captor.getValue().isPositive()).isTrue();
        assertThat(captor.getValue().getUserComments()).isNull();
        assertThat(response.isPositive()).isTrue();
    }

    @Test
    @DisplayName("throws ScanNotFoundException for an unknown scan id")
    void rejectsUnknownScan() {
        when(scanRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.submitFeedback(7L, 999L, false, null))
                .isInstanceOf(ScanNotFoundException.class);

        verify(scanFeedbackRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("propagates the family authorization failure for a scan outside the caller's family")
    void rejectsUnauthorizedScan() {
        Scan scan = new Scan();
        scan.setId(19L);
        scan.setProfileId(55L);
        when(scanRepository.findById(19L)).thenReturn(Optional.of(scan));
        org.mockito.Mockito.doThrow(new FamilyForbiddenException("Profile does not belong to your family circle."))
                .when(familyAuthorizationService).assertProfileAuthorizedForScan(eq(7L), eq(55L));

        assertThatThrownBy(() -> service.submitFeedback(7L, 19L, false, null))
                .isInstanceOf(FamilyForbiddenException.class);

        verify(scanFeedbackRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
