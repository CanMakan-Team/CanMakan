# Shared client assets

Brand images used by **web** and **mobile** live here so each platform does not
keep its own copy.

## Mascot poses

PNG files in [`mascot/drawable/`](./mascot/drawable/) use Android resource names
(`canmakan_mascot_wave.png`, and so on). `mascot/` is an extra Android `res`
root and must contain only resource-type folders such as `drawable/`.

| Client | How the folder is referenced |
| --- | --- |
| Mobile | Extra Android `res` directory at `mascot/` (`drawable/` is the resource type) |
| Web | Vite imports the PNGs from `drawable/` (hashed asset URLs at build) |

Edit the files in `mascot/drawable/` only. Do not copy them into
`client/web/public` or `client/mobile/app/src/main/res/drawable`.

The invitation email still embeds a classpath copy at
`server/backend/src/main/resources/email/canmakan-mascot-wave.png`.
When that CID attach is missing, the HTML falls back to the hosted URL
`/email/canmakan-mascot-wave.png`, which Vite serves from this folder.
