import type { FamilyMember } from '../../../shared/api/types'
import { formatCode } from './profileOptions'

/** Labels next to a family profile. "Self" is the signed-in account, not relationship SELF. */
export function profileDisplayCaption(
  member: Pick<FamilyMember, 'profileId' | 'relationship' | 'memberRole'>,
  viewer: { selfProfileId: number | null; isPrimaryAdmin: boolean },
): string {
  const parts: string[] = []
  if (member.memberRole === 'PRIMARY_ADMIN') {
    parts.push('Admin')
  }
  if (viewer.selfProfileId != null && member.profileId === viewer.selfProfileId) {
    parts.push('Self')
  } else if (viewer.isPrimaryAdmin) {
    const relationship = formatRelationshipCaption(member.relationship)
    if (relationship) {
      parts.push(relationship)
    }
  }
  return parts.join(' · ')
}

function formatRelationshipCaption(relationship: string | undefined): string | null {
  const trimmed = relationship?.trim() ?? ''
  if (!trimmed || trimmed.toUpperCase() === 'SELF') {
    return null
  }
  return formatCode(trimmed)
}
