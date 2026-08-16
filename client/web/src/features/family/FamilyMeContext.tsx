import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { getErrorMessage } from '../../shared/api/apiErrors'
import type { FamilyMe } from '../../shared/api/types'
import { familyApiService } from './api/familyApiService'
import { FamilyMeContext, type FamilyMeState } from './familyMeState'
import { isPrimaryAdminRole } from './lib/familyRoles'

export function FamilyMeProvider({ children }: { children: ReactNode }) {
  const [family, setFamily] = useState<FamilyMe | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)

  const load = useCallback(() => {
    setLoading(true)
    setError('')
    return familyApiService.getMyFamilyOrNull().then(
      (next) => {
        setFamily(next)
        setLoading(false)
      },
      (caught: unknown) => {
        setFamily(null)
        setError(getErrorMessage(caught))
        setLoading(false)
      },
    )
  }, [])

  useEffect(() => {
    const timeoutId = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timeoutId)
  }, [load, attempt])

  const value = useMemo<FamilyMeState>(
    () => ({
      family,
      loading,
      error,
      reload: () => setAttempt((current) => current + 1),
      isPrimaryAdmin: isPrimaryAdminRole(family?.memberRole),
      hasFamily: family != null,
    }),
    [family, loading, error],
  )

  return <FamilyMeContext.Provider value={value}>{children}</FamilyMeContext.Provider>
}
