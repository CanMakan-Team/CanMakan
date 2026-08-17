import http from "k6/http";
import { check, sleep } from "k6";
import { htmlReport } from "https://raw.githubusercontent.com/benc-uk/k6-reporter/main/dist/bencuk.min.js";
import { textSummary } from "https://jslib.k6.io/k6-summary/0.0.2/index.js";

/**
 * @fileoverview This script is designed to be run in a CI/CD pipeline to perform a load test on the CanMakan backend API.
 * It simulates a number of concurrent users making requests to the API and checks that the response times and error rates meet specified thresholds.
 *
 * Usage:
 * 1. Set the required environment variables:
 *    - VITE_API_BASE_URL: The base URL of the API to test (e.g., https://api.staging.canmakan.space)
 *    - DAST_TEST_JWT: A valid JWT token for authentication
 * 2. Run the script using k6:
 *    k6 run .github/scripts/k6-load-test.js
 **/

// 1. Configure the test parameters
export const options = {
  stages: [
    { duration: "30s", target: 20 }, // Ramp-up to 20 concurrent users over 30s
    { duration: "1m", target: 20 },  // Sustain 20 users for 1 minute
    { duration: "30s", target: 0 },  // Ramp-down to 0 users
  ],
  thresholds: {
    // Fails the pipeline if requirements are not met
    http_req_duration: ["p(95)<500", "p(99)<2000"], // 95% of requests must complete below 500ms
    http_req_failed: ["rate<0.01"], // Error rate must be less than 1%
  },
};

const baseUrl = __ENV.API_BASE_URL || "https://api.staging.canmakan.space";
const testEmail = __ENV.TEST_EMAIL;
const testPassword = __ENV.TEST_PASSWORD;

// 2. Define the Virtual User Behavior
export default function () {
  // 1. User Login
  const loginPayload = JSON.stringify({
    email: testEmail,
    password: testPassword,
  });

  const loginRes = http.post(`${baseUrl}/api/auth/login`, loginPayload, {
    headers: { 
      'Content-Type': 'application/json',
      'X-CanMakan-Session-Request': '1'
    },
  });

  check(loginRes, {
    "Login Successful (Status 200)": (r) => r.status === 200,
  });

  let token;
  try {
    // Adjust 'token' based on the API's actual JSON response key
    token = loginRes.json("accessToken");
  } catch (e) {
    // Proceed safely if token parsing fails
  }

  // Simulate Human Think Time before the Next Action
  sleep(1 + Math.random() * 2);

  // 2. Data Retrieval
  if (token) {
    const dataRes = http.get(`${baseUrl}/api/profiles/me`, {
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${token}`,
      },
    });

    check(dataRes, {
      "Data Retrieval Successful (Status 200)": (r) => r.status === 200,
    });
  }

  // Simulate Human Think Time before the Next Iteration
  sleep(1 + Math.random() * 2);
}

// 3. Generate an HTML Report
export function handleSummary(data) {
  return {
    // 1. Generates the interactive HTML dashboard
    "result.html": htmlReport(data),
    
    // 2. Generates the markdown summary for GitHub Actions UI
    "github_summary.md": `### 📊 k6 Load Test Summary\n\`\`\`\n${textSummary(data, { indent: " ", enableColors: false })}\n\`\`\``,

    // 3. Generates the JSON file expected by your artifact upload step
    "results.json": JSON.stringify(data, null, 2),
  };
}
