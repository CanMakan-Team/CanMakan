import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import { SessionProvider } from "./features/auth/SessionProvider";
import { AppRoutes } from "./app/router/AppRoutes";
import { AppErrorBoundary } from "./app/AppErrorBoundary";
import { initAmplitude } from "./app/analytics";
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
initAmplitude();
