import http from "k6/http";
import { check, group, sleep } from "k6";
import exec from "k6/execution";
import { htmlReport } from "https://raw.githubusercontent.com/benc-uk/k6-reporter/3.0.4/dist/bundle.js";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.0.2/index.js";

/**
 * Staging load test for the CanMakan scan journey.
 *
 * Each virtual user is already authenticated (token from setup) and then
 * repeats the mobile-style path: restore session, assess a barcode, fetch
 * alternatives, then load personal scan history.
 *
 * POST /api/scan/validate is skipped on purpose. The combined scan path
 * looks up the product inside assess; calling validate as well would double
 * Open Food Facts traffic without extra backend coverage.
 *
 * Usage:
 *   API_BASE_URL, TEST_EMAIL, TEST_PASSWORD
 *   optional TEST_BARCODE (default Nutella, same barcode as backend smoke tests)
 *   k6 run .github/scripts/k6-load-test.js
 */

const JSON_HEADERS = { "Content-Type": "application/json" };
const SESSION_HEADER = { "X-CanMakan-Session-Request": "1" };

/* Set options for the load test */
export const options = {
  stages: [
    { duration: "30s", target: 20 },
    { duration: "1m", target: 20 },
    { duration: "30s", target: 0 },
  ],
  thresholds: {
    checks: ["rate>0.99"],
    http_req_failed: ["rate<0.01"],
    // Lightweight authenticated reads should stay under the existing 500ms P95.
    "http_req_duration{name:login}": ["p(95)<500", "p(99)<2000"],
    "http_req_duration{name:session}": ["p(95)<500", "p(99)<2000"],
    "http_req_duration{name:profile}": ["p(95)<500", "p(99)<2000"],
    "http_req_duration{name:restrictions}": ["p(95)<500", "p(99)<2000"],
    "http_req_duration{name:history}": ["p(95)<500", "p(99)<2000"],
    // Assess may call Open Food Facts, persist a scan, and sometimes escalate.
    "http_req_duration{name:assess}": ["p(95)<4000", "p(99)<10000"],
    // Recommendations may call the Python TF-IDF ranker.
    "http_req_duration{name:recommendations}": ["p(95)<2000", "p(99)<5000"],
  },
};

const baseUrl = (__ENV.API_BASE_URL || "https://api.staging.canmakan.space").replace(
  /\/$/,
  ""
);
const testEmail = __ENV.TEST_EMAIL;
const testPassword = __ENV.TEST_PASSWORD;
const testBarcode = __ENV.TEST_BARCODE || "3017620422003";

/* Helper functions */
function authHeaders(token) {
  return {
    ...JSON_HEADERS,
    Authorization: `Bearer ${token}`,
  };
}

function jsonField(response, path) {
  try {
    return response.json(path);
  } catch (e) {
    return undefined;
  }
}

/* 1. Login POST request */
function login() {
  return http.post(
    `${baseUrl}/api/auth/login`,
    JSON.stringify({
      email: testEmail,
      password: testPassword,
    }),
    {
      headers: { ...JSON_HEADERS, ...SESSION_HEADER },
      tags: { name: "login" },
    }
  );
}
/* 2. Setup journey */
export function setup() {
  if (!testEmail || !testPassword) {
    exec.test.abort("TEST_EMAIL and TEST_PASSWORD must be set");
  }

  const loginRes = login();
  const loginOk = check(loginRes, {
    "setup: login returns 200": (r) => r.status === 200,
  });
  const token = jsonField(loginRes, "accessToken");
  if (!loginOk || !token) {
    exec.test.abort(`login failed with HTTP ${loginRes.status}`);
  }

  const profileRes = http.get(`${baseUrl}/api/profiles/me`, {
    headers: authHeaders(token),
    tags: { name: "profile" },
  });
  const profileOk = check(profileRes, {
    "setup: profile returns 200": (r) => r.status === 200,
  });
  const profileId = jsonField(profileRes, "profileId");
  if (!profileOk || profileId === undefined || profileId === null) {
    exec.test.abort(`GET /api/profiles/me failed with HTTP ${profileRes.status}`);
  }

  return { token, profileId };
}

/* 3. Scan journey */
export default function scanJourney(data) {
  const token = data.token;
  const profileId = data.profileId;
  const headers = authHeaders(token);

  group("restore session", function () {
    const meRes = http.get(`${baseUrl}/api/auth/me`, {
      headers,
      tags: { name: "session" },
    });
    check(meRes, {
      "GET /api/auth/me is 200": (r) => r.status === 200,
    });

    const profileRes = http.get(`${baseUrl}/api/profiles/me`, {
      headers,
      tags: { name: "profile" },
    });
    check(profileRes, {
      "GET /api/profiles/me is 200": (r) => r.status === 200,
    });

    const restrictionsRes = http.get(
      `${baseUrl}/api/profiles/${profileId}/restrictions`,
      {
        headers,
        tags: { name: "restrictions" },
      }
    );
    check(restrictionsRes, {
      "GET profile restrictions is 200": (r) => r.status === 200,
    });
  });

  sleep(0.5 + Math.random());

  group("scan product", function () {
    const assessRes = http.post(
      `${baseUrl}/api/scan/assess`,
      JSON.stringify({
        barcode: testBarcode,
        profileId,
      }),
      {
        headers,
        tags: { name: "assess" },
      }
    );

    const verdict = jsonField(assessRes, "verdict");
    const scanId = jsonField(assessRes, "scanId");
    check(assessRes, {
      "POST /api/scan/assess is 200": (r) => r.status === 200,
      "assess returns a verdict": () =>
        verdict === "SAFE" || verdict === "WARNING" || verdict === "UNSAFE",
    });

    const recParams = [`sourceBarcode=${encodeURIComponent(testBarcode)}`];
    if (scanId !== undefined && scanId !== null) {
      recParams.push(`scanId=${encodeURIComponent(String(scanId))}`);
    }
    const recRes = http.get(
      `${baseUrl}/api/profiles/${profileId}/recommendations?${recParams.join("&")}`,
      {
        headers,
        tags: { name: "recommendations" },
      }
    );
    check(recRes, {
      "GET recommendations is 200": (r) => r.status === 200,
    });

    const historyRes = http.get(`${baseUrl}/api/scan/history/${profileId}`, {
      headers,
      tags: { name: "history" },
    });
    check(historyRes, {
      "GET scan history is 200": (r) => r.status === 200,
    });
  });

  sleep(1 + Math.random() * 2);
}

/* 4. Handle summary */
export function handleSummary(data) {
  return {
    "summary.html": htmlReport(data),
    "github_summary.md": `### 📊 k6 Load Test Summary\n\`\`\`\n${textSummary(data, { indent: " ", enableColors: false })}\n\`\`\``,
    "summary.json": JSON.stringify(data, null, 2),
  };
}
