import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import * as amplitude from "@amplitude/analytics-browser";
import { SessionProvider } from "./features/auth/SessionProvider";
import { AppRoutes } from "./app/router/AppRoutes";
import { AppErrorBoundary } from "./app/AppErrorBoundary";
import "./app/documentIcons";
import "./styles/app.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <AppErrorBoundary>
      <BrowserRouter>
        <SessionProvider>
          <AppRoutes />
        </SessionProvider>
      </BrowserRouter>
    </AppErrorBoundary>
  </StrictMode>,
);

// Defensive initialization
const amplitudeKey = import.meta.env.VITE_AMPLITUDE_API_KEY;

if (amplitudeKey) {
  amplitude.init(amplitudeKey, {
    defaultTracking: {
      pageViews: true,
      sessions: true,
      formInteractions: false,
      fileDownloads: false,
    },
  });
} else if (import.meta.env.DEV) {
  console.warn("Amplitude API key is missing. Analytics are disabled for this environment.");
}
