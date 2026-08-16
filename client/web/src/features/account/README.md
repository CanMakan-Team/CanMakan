# features/account

Personal USER desk: home, SELF dietary setup, and account settings.

These screens do not require a Family Circle. Day-to-day scanning stays in the
mobile app.

## Routes

- `/me` — personal home (status cards, setup progress, mobile app banner). The
  banner QR encodes `VITE_FIREBASE_APP_DISTRIBUTION_URL` (GitHub secret
  `FIREBASE_APP_DISTRIBUTION_URL` at deploy), falling back to
  `https://appdistribution.firebase.google.com/`.
- `/me/setup-profile` — optional SELF dietary profile
- `/me/account` — account settings and delete account

Legacy `/family/personal`, `/family/setup-profile`, and `/family/account` redirect here.
