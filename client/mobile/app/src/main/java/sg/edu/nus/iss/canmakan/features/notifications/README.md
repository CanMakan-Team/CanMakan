# features/notifications

Account-wide inbox opened from the top-bar bell. Family invites are one source;
other features can add cards of their own `type`.

## API

Owned by `features/notifications/data` (`GET /api/notifications/me`,
`POST /api/notifications/me/read`, `DELETE /api/notifications/{id}`).

| Type | Typical UI |
| --- | --- |
| `FAMILY_INVITE_UPDATE` | Status copy (sent / joined / declined). No invite buttons. |
| `FAMILY_INVITE_REQUEST` | `Join {family}?` plus **Accept** / **Decline** |

Accept and decline still call family invitation endpoints
(`POST /api/invitations/{token}/accept` and `…/decline`).

The close control on a card calls `DELETE /api/notifications/{id}`. That only
removes the inbox row; it does not decline an invitation.

Opening the inbox also calls `POST /api/notifications/me/read`.

New users see a `FAMILY_INVITE_REQUEST` card after they register (hydration from
PENDING invites for their email). Until then they use the invitation email.
