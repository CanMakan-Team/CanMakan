# features/account

Personal USER desk: home, SELF dietary setup, and account settings.

These screens do not require a Family Circle. Day-to-day scanning stays in the
mobile app.

## Routes

- `/me` — personal home (status cards, setup progress, mobile app banner). The
  banner is shown only when the account has no personal scans. The QR encodes
  `VITE_FIREBASE_APP_DISTRIBUTION_URL` (GitHub secret
  `FIREBASE_APP_DISTRIBUTION_URL` at deploy), falling back to
  `https://appdistribution.firebase.google.com/`. “I already have it installed”
  hides the banner for that user (`localStorage` key
  `canmakan.home.mobile-app-installed.{userId}`). Tester-only App Distribution
  copy is a dismissible notice (`localStorage` key
  `canmakan.home.tester-distribution-notice.dismissed`) shown with the banner.
- `/me/setup-profile` — optional SELF dietary profile
- `/me/account` — account settings and delete account

Legacy `/family/personal`, `/family/setup-profile`, and `/family/account` redirect here.
