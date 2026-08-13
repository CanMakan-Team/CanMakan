# Notifications APIs

Account inbox for any feature. Family invites are one source of cards; other
features can write the same table without going through family APIs.

Protected under `/api/notifications/**` (authenticated JWT).

## Model

`user_notifications` stores:

| Field | Purpose |
| --- | --- |
| `type` | Card kind (`FAMILY_INVITE_UPDATE`, `FAMILY_INVITE_REQUEST`, …) |
| `title` / `body` | Display copy |
| `reference_type` / `reference_id` | Optional source (e.g. `INVITATION` + id) for in-place upsert |
| `action_token` | Optional token for an in-app action (invite accept/decline) |
| `expires_at` | Optional; listed as `expired` when in the past |
| `read_at` | Set when the caller marks the inbox read |

Unique key: `(user_id, type, reference_type, reference_id)`. Listing does **not**
recreate deleted cards.

## List

`GET /api/notifications/me`

Newest `updatedAt` first.

```json
[
  {
    "id": 1,
    "type": "FAMILY_INVITE_UPDATE",
    "title": "Invite sent to jamie@example.com.",
    "body": "Wong Family",
    "actionToken": null,
    "expired": false,
    "read": false,
    "updatedAt": "2026-08-14T04:00:00Z"
  }
]
```

`actionToken` is present when the card has an in-app action. For family invites
that is `FAMILY_INVITE_REQUEST` while the invite is still pending.

## Mark read

`POST /api/notifications/me/read`

Sets `read_at` on the caller's unread rows. Does not change `updatedAt`. Returns **204**.

## Delete

`DELETE /api/notifications/{id}`

Removes that inbox row for the caller only. Returns **204**. **404** if the id is
missing or belongs to someone else.

Delete does **not** decline a family invitation. Accept and decline still use
`POST /api/invitations/{token}/accept` and `…/decline`.

## Family invite cards (writer: `FamilyInviteNotifier`)

| Event | Admin (`FAMILY_INVITE_UPDATE`) | Invitee (`FAMILY_INVITE_REQUEST`) |
| --- | --- | --- |
| Invite emailed | `Invite sent to jamie@example.com.` | If they already have an account: `Join Wong Family?` |
| Register with a pending invite | unchanged | Created on register |
| Accept or claim | `Jamie joined your family.` | Removed |
| Decline | `Jamie declined this time.` | Removed |

Failed email sends do not create cards (the PENDING invitation is deleted).
Deleting a card from the inbox only hides it; it does not change invitation status.
