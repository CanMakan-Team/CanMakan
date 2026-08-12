package sg.edu.nus.iss.canmakan.features.auth.onboarding

import javax.inject.Inject
import sg.edu.nus.iss.canmakan.features.family.data.FamilyProfileRepository

interface PendingInvitationClaimer {
    suspend fun claim(invitationToken: String)
}

class FamilyPendingInvitationClaimer @Inject constructor(
    private val familyProfileRepository: FamilyProfileRepository,
) : PendingInvitationClaimer {
    override suspend fun claim(invitationToken: String) {
        familyProfileRepository.claimInvitation(invitationToken)
    }
}
