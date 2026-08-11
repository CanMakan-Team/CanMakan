export type AdminUserRole = 'USER' | 'ADMIN'

export interface AdminUser {
  userId: number
  email: string
  role: AdminUserRole
  active: boolean
  updatedAt: string
}

export interface AdminUserFilters {
  query?: string
  role?: AdminUserRole
  active?: boolean
}

export interface UpdateAccountStatusInput {
  active: boolean
  reason: string
}

export interface UpdateAccountStatusResponse extends AdminUser {
  changed: boolean
}
