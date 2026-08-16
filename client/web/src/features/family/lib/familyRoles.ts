export const PRIMARY_ADMIN_ROLE = 'PRIMARY_ADMIN'

export function isPrimaryAdminRole(memberRole: string | null | undefined): boolean {
  return memberRole === PRIMARY_ADMIN_ROLE
}
