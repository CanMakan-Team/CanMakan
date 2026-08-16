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

// UC20 admin review: thumbs up/down feedback reported against a scan verdict.
export interface AdminScanFeedbackItem {
  id: number
  scanId: number
  userEmail: string | null
  productName: string
  isPositive: boolean
  userComments: string | null
  resolved: boolean
  createdAt: string
}

export interface AdminScanFeedbackSummary {
  totalFeedback: number
  negativePercentage: number
  feedbackPerDay: number
  negativeFeedbackPerDay: number
}

export interface AdminScanFeedbackPageInfo {
  page: number
  pageSize: number
  totalItems: number
  totalPages: number
}

export interface AdminScanFeedbackListResponse {
  summary: AdminScanFeedbackSummary
  items: AdminScanFeedbackItem[]
  pageInfo: AdminScanFeedbackPageInfo
}

export interface AdminScanFeedbackFilters {
  keyword?: string
  restrictionCode?: string
  periodDays?: number
  isPositive?: boolean
  resolved?: boolean
  page?: number
  pageSize?: number
}

export interface UpdateScanFeedbackResolvedResponse {
  id: number
  resolved: boolean
}
