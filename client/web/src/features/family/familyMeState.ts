import { createContext } from 'react'
import type { FamilyMe } from '../../shared/api/types'

export type FamilyMeState = {
  family: FamilyMe | null
  loading: boolean
  error: string
  reload: () => void
  isPrimaryAdmin: boolean
  hasFamily: boolean
}

export const FamilyMeContext = createContext<FamilyMeState | null>(null)
