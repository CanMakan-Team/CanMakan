import { useContext } from 'react'
import { FamilyMeContext, type FamilyMeState } from './familyMeState'

export function useFamilyMe(): FamilyMeState {
  const context = useContext(FamilyMeContext)
  if (!context) {
    throw new Error('useFamilyMe must be used within FamilyMeProvider')
  }
  return context
}
