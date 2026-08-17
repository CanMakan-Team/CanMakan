export const PRIMARY_ADMIN_ROLE = 'PRIMARY_ADMIN'

export function isPrimaryAdminRole(memberRole: string | null | undefined): boolean {
  return memberRole === PRIMARY_ADMIN_ROLE
}

/**
 * True when this roster row is the signed-in family admin. That profile cannot
 * be deactivated or removed from the Family Members page.
 */
export function isCurrentAdminProfile(
  member: { profileId: number; memberRole?: string | null },
  selfProfileId: number | null,
): boolean {
  if (selfProfileId != null) {
    return member.profileId === selfProfileId
  }
  return isPrimaryAdminRole(member.memberRole)
}
