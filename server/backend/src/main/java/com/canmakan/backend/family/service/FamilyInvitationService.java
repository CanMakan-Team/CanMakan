package com.canmakan.backend.family.service;

import com.canmakan.backend.dietaryprofile.model.DietaryProfile;
import com.canmakan.backend.dietaryprofile.repository.DietaryProfileRepository;
import com.canmakan.backend.family.config.InviteProperties;
import com.canmakan.backend.family.dto.ClaimInvitationRequest;
import com.canmakan.backend.family.dto.CreateInvitationRequest;
import com.canmakan.backend.family.dto.FamilyMeResponse;
import com.canmakan.backend.family.dto.InvitationPreviewResponse;
import com.canmakan.backend.family.dto.InvitationResponse;
import com.canmakan.backend.family.dto.PendingInvitationResponse;
import com.canmakan.backend.family.exception.AlreadyInFamilyException;
import com.canmakan.backend.family.exception.FamilyForbiddenException;
import com.canmakan.backend.family.exception.FamilyNotFoundException;
import com.canmakan.backend.family.exception.InvitationConflictException;
import com.canmakan.backend.family.exception.InvitationExpiredException;
import com.canmakan.backend.family.exception.InvitationNotFoundException;
import com.canmakan.backend.family.model.Family;
import com.canmakan.backend.family.model.FamilyInvitation;
import com.canmakan.backend.family.model.FamilyMember;
import com.canmakan.backend.family.model.InvitationStatus;
import com.canmakan.backend.family.repository.FamilyInvitationRepository;
import com.canmakan.backend.family.repository.FamilyMemberRepository;
import com.canmakan.backend.family.repository.FamilyRepository;
import com.canmakan.backend.shared.exception.AuthenticatedUserNotFoundException;
import com.canmakan.backend.user.model.UserAccount;
import com.canmakan.backend.user.repository.UserAccountRepository;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FamilyInvitationService {

    private static final String INVITE_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int INVITE_CODE_LENGTH = 8;
    private static final int INVITE_TOKEN_BYTES = 24;

    private final UserAccountRepository userAccountRepository;
    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final FamilyInvitationRepository familyInvitationRepository;
    private final DietaryProfileRepository dietaryProfileRepository;
    private final FamilyAuthorizationService familyAuthorization;
    private final InviteProperties inviteProperties;
    private final InvitationEmailService invitationEmailService;
    private final FamilyInviteNotifier familyInviteNotifier;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public InvitationResponse createInvitation(long adminUserId, CreateInvitationRequest request) {
        FamilyMember adminMembership = familyAuthorization.requirePrimaryAdmin(adminUserId);
        String email = FamilyDisplayUtil.normalizeEmail(request.email());
        Optional<UserAccount> invitee = userAccountRepository.findByEmail(email);

        if (invitee.isPresent() && familyMemberRepository.existsByIdUserId(invitee.get().getId())) {
            throw new InvitationConflictException(
                "That user already belongs to a family circle.");
        }

        Optional<FamilyInvitation> existingPending = familyInvitationRepository
            .findPendingByFamilyAndEmail(adminMembership.getFamilyId(), email);
        if (existingPending.isPresent()) {
            throw new InvitationConflictException(
                "An invitation email was already sent to this address.");
        }

        Instant expiresAt = Instant.now()
            .plus(inviteProperties.getExpiryDays(), ChronoUnit.DAYS);
        FamilyInvitation invitation = new FamilyInvitation();
        invitation.setFamilyId(adminMembership.getFamilyId());
        invitation.setInvitedByUserId(adminUserId);
        invitation.setInvitedEmail(email);
        invitation.setRelationship(request.relationship());
        invitation.setInvitationToken(generateUniqueInvitationToken());
        invitation.setInviteCode(generateUniqueInviteCode());
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setExpiresAt(expiresAt);

        FamilyInvitation saved = familyInvitationRepository.saveAndFlush(invitation);
        InvitationResponse response = deliverInvitationEmail(saved, invitee.isPresent());
        if (!response.emailSent()) {
            familyInvitationRepository.delete(saved);
            familyInvitationRepository.flush();
            return response;
        }
        familyInviteNotifier.notifyInviteSent(saved, invitee.orElse(null));
        return response;
    }

    @Transactional
    public FamilyMeResponse claimInvitation(long userId, ClaimInvitationRequest request) {
        return acceptInvitation(userId, request.invitationToken(), request.profileName());
    }

    @Transactional
    public FamilyMeResponse acceptInvitation(long userId, String invitationToken) {
        return acceptInvitation(userId, invitationToken, null);
    }

    @Transactional
    public FamilyMeResponse acceptInvitation(long userId, String invitationToken, String profileName) {
        UserAccount user = userAccountRepository.findById(userId)
            .orElseThrow(() -> new AuthenticatedUserNotFoundException(
                "Authenticated user was not found."));
        if (invitationToken == null || invitationToken.isBlank()) {
            throw new IllegalArgumentException("Invitation token is required.");
        }
        FamilyInvitation invitation = resolveClaimableInvitation(
            FamilyDisplayUtil.normalizeEmail(user.getEmail()), invitationToken.strip());
        return applyInvitationClaim(user, invitation, profileName);
    }

    @Transactional
    public void declineInvitation(long userId, String invitationToken) {
        UserAccount user = userAccountRepository.findById(userId)
            .orElseThrow(() -> new AuthenticatedUserNotFoundException(
                "Authenticated user was not found."));
        if (invitationToken == null || invitationToken.isBlank()) {
            throw new IllegalArgumentException("Invitation token is required.");
        }
        FamilyInvitation invitation = familyInvitationRepository
            .findByInvitationToken(invitationToken.strip())
            .orElseThrow(() -> new InvitationNotFoundException("Invitation was not found."));

        ensureEmailMatches(invitation, FamilyDisplayUtil.normalizeEmail(user.getEmail()));
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new InvitationConflictException("Invitation is no longer pending.");
        }

        invitation.setStatus(InvitationStatus.DECLINED);
        familyInvitationRepository.saveAndFlush(invitation);
        familyInviteNotifier.notifyInviteDeclined(invitation, user.getEmail());
    }

    @Transactional(readOnly = true)
    public List<PendingInvitationResponse> listMyPendingInvitations(long userId) {
        UserAccount user = userAccountRepository.findById(userId)
            .orElseThrow(() -> new AuthenticatedUserNotFoundException(
                "Authenticated user was not found."));
        String email = FamilyDisplayUtil.normalizeEmail(user.getEmail());
        List<FamilyInvitation> pending = familyInvitationRepository.findPendingByEmail(email);

        List<PendingInvitationResponse> results = new ArrayList<>();
        for (FamilyInvitation invitation : pending) {
            Family family = familyRepository.findById(invitation.getFamilyId()).orElse(null);
            String familyName = family == null ? "Family" : family.getFamilyName();
            String invitedBy = userAccountRepository.findById(invitation.getInvitedByUserId())
                .map(account -> account.getEmail())
                .orElse("Family admin");
            results.add(new PendingInvitationResponse(
                invitation.getId(),
                invitation.getFamilyId(),
                familyName,
                invitedBy,
                invitation.getInvitationToken(),
                invitation.getInviteCode(),
                invitation.getStatus(),
                invitation.getExpiresAt(),
                isExpired(invitation)
            ));
        }
        return results;
    }

    @Transactional(readOnly = true)
    public InvitationPreviewResponse previewInvitation(String invitationToken) {
        if (invitationToken == null || invitationToken.isBlank()) {
            throw new InvitationNotFoundException("Invitation was not found.");
        }
        FamilyInvitation invitation = familyInvitationRepository
            .findByInvitationToken(invitationToken.strip())
            .orElseThrow(() -> new InvitationNotFoundException("Invitation was not found."));
        String familyName = familyRepository.findById(invitation.getFamilyId())
            .map(family -> family.getFamilyName())
            .orElse("a family circle");
        return new InvitationPreviewResponse(
            invitation.getInvitedEmail(),
            familyName,
            isExpired(invitation)
        );
    }

    private InvitationResponse deliverInvitationEmail(
            FamilyInvitation invitation, boolean inviteeRegistered) {
        InvitationResponse pendingResponse = toInvitationResponse(invitation, inviteeRegistered, false);
        Family family = familyRepository.findById(invitation.getFamilyId()).orElse(null);
        String familyName = family == null ? "a family circle" : family.getFamilyName();
        boolean emailSent = invitationEmailService.sendInvitationEmail(familyName, pendingResponse);
        return toInvitationResponse(invitation, inviteeRegistered, emailSent);
    }

    private FamilyInvitation resolveClaimableInvitation(
            String normalizedEmail, String optionalToken) {
        if (optionalToken != null && !optionalToken.isBlank()) {
            FamilyInvitation byToken = familyInvitationRepository
                .findByInvitationToken(optionalToken.strip())
                .orElseThrow(() -> new InvitationNotFoundException(
                    "Invitation was not found."));
            ensureAcceptable(byToken, normalizedEmail);
            return byToken;
        }

        List<FamilyInvitation> pending =
            familyInvitationRepository.findPendingByEmail(normalizedEmail);
        List<FamilyInvitation> valid = pending.stream()
            .filter(this::isPendingAndUnexpired)
            .toList();
        if (valid.isEmpty()) {
            return null;
        }
        if (valid.size() > 1) {
            throw new InvitationConflictException(
                "Multiple pending invitations found; provide an invitation token.");
        }
        return valid.get(0);
    }

    private void ensureAcceptable(FamilyInvitation invitation, String normalizedEmail) {
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new InvitationConflictException("Invitation is no longer valid.");
        }
        if (isExpired(invitation)) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            familyInvitationRepository.saveAndFlush(invitation);
            throw new InvitationExpiredException("Invitation has expired.");
        }
        ensureEmailMatches(invitation, normalizedEmail);
    }

    private void ensureEmailMatches(FamilyInvitation invitation, String normalizedEmail) {
        if (!invitation.getInvitedEmail().equalsIgnoreCase(normalizedEmail)) {
            throw new FamilyForbiddenException(
                "Invitation email does not match the authenticated user.");
        }
    }

    private boolean isPendingAndUnexpired(FamilyInvitation invitation) {
        return invitation.getStatus() == InvitationStatus.PENDING && !isExpired(invitation);
    }

    private boolean isExpired(FamilyInvitation invitation) {
        return invitation.getExpiresAt() == null
            || !invitation.getExpiresAt().isAfter(Instant.now());
    }

    private FamilyMeResponse applyInvitationClaim(
            UserAccount user, FamilyInvitation invitation, String profileName) {
        if (familyMemberRepository.existsByIdUserId(user.getId())) {
            throw new AlreadyInFamilyException("You already belong to a family circle.");
        }

        Family family = familyRepository.findById(invitation.getFamilyId())
            .orElseThrow(() -> new FamilyNotFoundException(FamilyAuthorizationService.NOT_IN_FAMILY_MESSAGE));

        try {
            FamilyMember membership = new FamilyMember();
            membership.setId(new FamilyMember.FamilyMemberId(family.getId(), user.getId()));
            membership.setMemberRole(FamilyMember.ROLE_MEMBER);
            membership.setIsActive(true);
            familyMemberRepository.saveAndFlush(membership);

            DietaryProfile selfProfile = dietaryProfileRepository.findByLinkedUser_Id(user.getId())
                .orElseGet(DietaryProfile::new);
            selfProfile.setFamily(family);
            selfProfile.setLinkedUser(user);
            if (profileName != null && !profileName.isBlank()) {
                selfProfile.setProfileName(profileName.trim());
            } else if (selfProfile.getProfileName() == null || selfProfile.getProfileName().isBlank()) {
                selfProfile.setProfileName(FamilyDisplayUtil.profileNameFromUser(user));
            }
            selfProfile.setRelationship(invitation.getRelationship());
            selfProfile.setPrimary(false);
            DietaryProfile savedProfile = dietaryProfileRepository.saveAndFlush(selfProfile);

            invitation.setStatus(InvitationStatus.ACCEPTED);
            familyInvitationRepository.saveAndFlush(invitation);
            familyInviteNotifier.notifyInviteAccepted(invitation, user.getEmail());

            return new FamilyMeResponse(
                family.getId(),
                family.getFamilyName(),
                FamilyMember.ROLE_MEMBER,
                savedProfile.getId(),
                family.getCreatedByUserId()
            );
        } catch (DataIntegrityViolationException ex) {
            if (FamilyDisplayUtil.isMembershipUniqueViolation(ex)) {
                throw new AlreadyInFamilyException("You already belong to a family circle.");
            }
            throw ex;
        }
    }

    private InvitationResponse toInvitationResponse(
            FamilyInvitation invitation, boolean inviteeRegistered, boolean emailSent) {
        String base = inviteProperties.getPublicBaseUrl();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String inviteUrl = base + "/invite/" + invitation.getInvitationToken();
        return new InvitationResponse(
            invitation.getId(),
            invitation.getInvitedEmail(),
            invitation.getRelationship(),
            invitation.getInvitationToken(),
            invitation.getInviteCode(),
            inviteUrl,
            invitation.getStatus(),
            invitation.getExpiresAt(),
            inviteeRegistered,
            emailSent
        );
    }

    private String generateUniqueInvitationToken() {
        for (int attempt = 0; attempt < 8; attempt++) {
            byte[] bytes = new byte[INVITE_TOKEN_BYTES];
            secureRandom.nextBytes(bytes);
            String token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            if (!familyInvitationRepository.existsByInvitationToken(token)) {
                return token;
            }
        }
        throw new IllegalStateException("Unable to generate a unique invitation token.");
    }

    private String generateUniqueInviteCode() {
        for (int attempt = 0; attempt < 16; attempt++) {
            StringBuilder code = new StringBuilder(INVITE_CODE_LENGTH);
            for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
                int index = secureRandom.nextInt(INVITE_CODE_ALPHABET.length());
                code.append(INVITE_CODE_ALPHABET.charAt(index));
            }
            String candidate = code.toString();
            if (!familyInvitationRepository.existsByInviteCode(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate a unique invite code.");
    }
}
