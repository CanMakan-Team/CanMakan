# Shared client assets

Brand images used by **web** and **mobile** live here so each platform does not
keep its own copy.

## Mascot poses

PNG files in [`drawable/`](./drawable/) use Android resource names
(`canmakan_mascot_wave.png`, and so on).

| Client | How the folder is referenced |
| --- | --- |
| Mobile | Extra Android `res` directory (`drawable/` is the resource type) |
| Web | Vite serves and ships them as `/mascot/canmakan-mascot-*.png` |

Edit the files in this folder only. Do not copy them into
`client/web/public` or `client/mobile/app/src/main/res/drawable`.

The invitation email still embeds a classpath copy at
`server/backend/src/main/resources/email/canmakan-mascot-wave.png`.
The web hosted fallback URL `/email/canmakan-mascot-wave.png` is served
from this folder by Vite.
