package sg.edu.nus.iss.canmakan.features.family.model

/**
 * Relationship of a dependant family member to the family's admin.
 *
 * The enum name is sent to the backend as-is (already UPPERCASE) so it matches
 * the `relationship` column values expected by the `dietary_profiles` table.
 * [displayName] is the human-readable label shown in the UI.
 *
 * This set and its display names intentionally mirror the web app's
 * `Relationship` type (client/web/src/shared/api/types.ts) and
 * `relationshipOptions` (client/web/src/features/family/lib/profileOptions.ts),
 * minus `SELF`: only the family admin can reach the Add dependant profile
 * screen, and `SELF` is auto-assigned by the backend to the admin's own
 * profile rather than something chosen for a dependant. Keep the remaining
 * values in sync with web by hand if either list changes.
 *
 * Declaration order is also the display order used in the "Relationship to
 * Admin" dropdown on the Add dependant profile screen.
 */
enum class RelationshipToAdmin(val displayName: String) {
    SPOUSE("Spouse"),
    CHILD("Child"),
    PARENT("Parent"),
    DEPENDANT("Dependant"),
    OTHER("Other"),
}
