import type { ProfileRestrictionSeverity } from '../api/selfProfileApiService'

/** Builds the restriction map for self-profile save, preserving untouched persisted severities. */
export function buildRestrictionPayload(
  selected: Record<number, ProfileRestrictionSeverity>,
  persistedSeverities: Record<number, string>,
  touchedIds: Set<number>,
): Record<number, ProfileRestrictionSeverity> {
  return Object.entries(selected).reduce<Record<number, ProfileRestrictionSeverity>>(
    (accumulator, [restrictionId, severity]) => {
      const id = Number(restrictionId)
      const persistedSeverity = persistedSeverities[id]
      accumulator[id] =
        !touchedIds.has(id) && persistedSeverity
          ? (persistedSeverity as ProfileRestrictionSeverity)
          : severity
      return accumulator
    },
    {},
  )
}
