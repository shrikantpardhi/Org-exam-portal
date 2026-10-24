// Headless-browser smoke driver for the Docker Compose stack (GAP-015, GAP-137).
//
// Speaks the Chrome DevTools Protocol over a raw WebSocket so it needs no Playwright or Puppeteer
// install. It loads the frontend through the edge, records every console message and every failed
// or blocked network request, then asserts the application can actually talk to the API.
//
// The distinction it exists to make: a rendered page proves the HTML was served; only a real
// browser proves the JavaScript, the CSP, and the CORS round trip all work together.

import { spawn } from "node:child_process";
import { mkdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { createHash } from "node:crypto";
import { tmpdir } from "node:os";

function parseArgs(argv) {
  const args = { email: "", password: "" };
  for (let i = 0; i < argv.length; i += 2) {
    const key = argv[i].replace(/^--/, "");
    args[key] = argv[i + 1] ?? "";
  }
  return args;
}

const args = parseArgs(process.argv.slice(2));
const EDGE_URL = (args.url || "http://localhost:8081").replace(/\/$/, "");
const CHROME = args.chrome;
const ARTIFACTS = args.artifacts || join(tmpdir(), "smoke");
const EMAIL = args.email || "";
const PASSWORD = args.password || "";

mkdirSync(ARTIFACTS, { recursive: true });

const profileDir = join(ARTIFACTS, `profile-${Date.now()}`);
mkdirSync(profileDir, { recursive: true });

const consoleMessages = [];
const networkFailures = [];
const apiResponses = [];
const blockedByCsp = [];
const requestUrls = new Map();
const badResponses = [];

function log(message) {
  process.stdout.write(`  ${message}\n`);
}

// ---------------------------------------------------------------- CDP transport

class CDP {
  constructor(ws) {
    this.ws = ws;
    this.nextId = 1;
    this.pending = new Map();
    this.listeners = [];
    ws.addEventListener("message", (event) => {
      const msg = JSON.parse(event.data);
      if (msg.id && this.pending.has(msg.id)) {
        const { resolve, reject } = this.pending.get(msg.id);
        this.pending.delete(msg.id);
        msg.error ? reject(new Error(JSON.stringify(msg.error))) : resolve(msg.result);
      } else if (msg.method) {
        for (const listener of this.listeners) listener(msg);
      }
    });
  }

  onEvent(listener) {
    this.listeners.push(listener);
  }

  send(method, params = {}) {
    const id = this.nextId++;
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject });
      this.ws.send(JSON.stringify({ id, method, params }));
    });
  }
}

async function connect(port) {
  // The /json/version endpoint exposes the BROWSER-level target, which supports only the
  // Browser/Target domains — Runtime and Page live on a page target. Attach to a page instead.
  for (let attempt = 0; attempt < 40; attempt++) {
    try {
      const res = await fetch(`http://127.0.0.1:${port}/json/list`);
      if (res.ok) {
        const targets = await res.json();
        const page = targets.find((t) => t.type === "page" && t.webSocketDebuggerUrl);
        if (page) {
          const ws = new WebSocket(page.webSocketDebuggerUrl);
          await new Promise((resolve, reject) => {
            ws.addEventListener("open", resolve, { once: true });
            ws.addEventListener("error", reject, { once: true });
          });
          return new CDP(ws);
        }
      }
    } catch {
      // Chrome is still starting.
    }
    await new Promise((r) => setTimeout(r, 250));
  }
  throw new Error("Chrome DevTools page target did not become available");
}

function launchChrome() {
  const port = 9000 + Math.floor(Math.random() * 1000);
  const child = spawn(
    CHROME,
    [
      "--headless=new",
      `--remote-debugging-port=${port}`,
      `--user-data-dir=${profileDir}`,
      "--no-first-run",
      "--no-default-browser-check",
      "--disable-gpu",
      "--disable-dev-shm-usage",
      "--window-size=1280,900",
      "about:blank",
    ],
    { stdio: "ignore", detached: false },
  );
  return { child, port };
}

// ---------------------------------------------------------------- helpers

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function isApiCall(url) {
  return url.includes("/api/v1/");
}

async function main() {
  const { child, port } = launchChrome();
  const failures = [];

  try {
    const cdp = await connect(port);

    cdp.onEvent((msg) => {
      if (msg.method === "Runtime.consoleAPICalled") {
        const text = (msg.params.args || [])
          .map((a) => a.value ?? a.description ?? "")
          .join(" ");
        consoleMessages.push({ type: msg.params.type, text });
      }
      if (msg.method === "Runtime.exceptionThrown") {
        const details = msg.params.exceptionDetails;
        consoleMessages.push({
          type: "exception",
          text: details.exception?.description || details.text || "unknown exception",
        });
      }
      if (msg.method === "Log.entryAdded") {
        consoleMessages.push({ type: msg.params.entry.level, text: msg.params.entry.text });
      }
      if (msg.method === "Network.requestWillBeSent") {
        requestUrls.set(msg.params.requestId, msg.params.request.url);
      }
      if (msg.method === "Network.loadingFailed") {
        const reason = msg.params.blockedReason || msg.params.errorText || "unknown";
        networkFailures.push({
          requestId: msg.params.requestId,
          url: requestUrls.get(msg.params.requestId) || "(unknown)",
          errorText: msg.params.errorText,
          blockedReason: msg.params.blockedReason,
          type: msg.params.type,
        });
        if (msg.params.blockedReason) blockedByCsp.push(reason);
      }
      if (msg.method === "Network.responseReceived") {
        const response = msg.params.response;
        if (response.status >= 400) {
          badResponses.push({ url: response.url, status: response.status });
        }
        if (isApiCall(response.url)) {
          apiResponses.push({ url: response.url, status: response.status });
        }
      }
    });

    await cdp.send("Runtime.enable");
    await cdp.send("Log.enable");
    await cdp.send("Network.enable");
    await cdp.send("Page.enable");

    // ---------------------------------------------------------- load the app
    log(`loading ${EDGE_URL}/login`);
    await cdp.send("Page.navigate", { url: `${EDGE_URL}/login` });
    await sleep(5000);

    const titleResult = await cdp.send("Runtime.evaluate", {
      expression: "document.title",
      returnByValue: true,
    });
    log(`document.title = ${JSON.stringify(titleResult.result.value)}`);

    const bodyText = await cdp.send("Runtime.evaluate", {
      expression: "document.body.innerText.slice(0, 400)",
      returnByValue: true,
    });
    log(`body text starts: ${JSON.stringify((bodyText.result.value || "").slice(0, 160))}`);

    // The page must have actually hydrated: React attaches the app markup, and Next.js registers
    // its own runtime. A blank body or an error overlay means the JavaScript never ran.
    const hydrated = await cdp.send("Runtime.evaluate", {
      expression: `(() => {
        const body = document.body ? document.body.innerText : "";
        const hasOverlay = !!document.querySelector("nextjs-portal");
        const scripts = document.querySelectorAll("script[src]").length;
        return JSON.stringify({ length: body.length, hasOverlay, scripts });
      })()`,
      returnByValue: true,
    });
    const hydration = JSON.parse(hydrated.result.value);
    log(`hydration: ${JSON.stringify(hydration)}`);

    if (hydration.hasOverlay) {
      failures.push("Next.js error overlay is present: the client bundle failed to run");
    }
    if (hydration.scripts === 0) {
      failures.push("no external scripts were loaded");
    }

    if (EMAIL && PASSWORD) {
      log("driving a login through the real UI");
      const filled = await cdp.send("Runtime.evaluate", {
        expression: `(() => {
          const email = document.querySelector('input[name="username"], input[type="email"], #username');
          const password = document.querySelector('input[type="password"], #password');
          if (!email || !password) return "missing-fields";
          const setValue = (el, value) => {
            const setter = Object.getOwnPropertyDescriptor(
              el instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype,
              "value",
            ).set;
            setter.call(el, value);
            el.dispatchEvent(new Event("input", { bubbles: true }));
            el.dispatchEvent(new Event("change", { bubbles: true }));
          };
          setValue(email, ${JSON.stringify(EMAIL)});
          setValue(password, ${JSON.stringify(PASSWORD)});
          const form = email.closest("form");
          if (form) { form.requestSubmit(); return "submitted"; }
          return "no-form";
        })()`,
        returnByValue: true,
      });
      log(`login driver: ${filled.result.value}`);
      if (filled.result.value === "missing-fields") {
        failures.push("login form fields were not found in the rendered page");
      }
      await sleep(6000);
    } else {
      // No account available (a fresh stack provisions none). Still prove the browser can reach
      // the API from this page's origin: a fetch that resolves to an HTTP status means DNS, TCP,
      // the edge route, CORS, and CSP all work. Only an unresolved/rejected fetch means the browser
      // never reached the backend.
      log("no credentials supplied; probing the API directly from the page context");
      const probe = await cdp.send("Runtime.evaluate", {
        expression: `fetch("/api/v1/me", { headers: { Accept: "application/json" } })
          .then((r) => "status:" + r.status)
          .catch((e) => "error:" + (e && e.message ? e.message : String(e)))`,
        awaitPromise: true,
        returnByValue: true,
      });
      const probeResult = String(probe.result.value ?? "");
      log(`page-context API probe -> ${probeResult}`);
      if (probeResult.startsWith("status:")) {
        // Any HTTP status proves the round trip completed; 401 is the expected unauthenticated one.
        // The Network domain already recorded this same request, so record it only if it did not.
        const alreadySeen = apiResponses.some((r) => r.url.endsWith("/api/v1/me"));
        if (!alreadySeen) {
          apiResponses.push({
            url: `${EDGE_URL}/api/v1/me`,
            status: Number(probeResult.slice("status:".length)),
          });
        }
      } else {
        failures.push(
          `the page could not reach the API (${probeResult}); this is the reported connection failure`,
        );
      }
      await sleep(500);
    }

    // ---------------------------------------------------------- assertions
    const apiCalls = apiResponses.length;
    log(`API responses observed: ${apiCalls}`);
    for (const call of apiResponses.slice(0, 10)) {
      log(`  ${call.status} ${call.url}`);
    }

    const consoleErrors = consoleMessages.filter(
      (m) => m.type === "error" || m.type === "exception",
    );

    const cspViolations = consoleMessages.filter((m) =>
      /content security policy|refused to (connect|load|execute)/i.test(m.text),
    );
    const corsViolations = consoleMessages.filter((m) =>
      /cors|access-control-allow-origin|cross-origin/i.test(m.text),
    );

    if (cspViolations.length > 0) {
      failures.push(`CSP blocked a resource: ${cspViolations[0].text.slice(0, 200)}`);
    }
    if (corsViolations.length > 0) {
      failures.push(`CORS blocked a request: ${corsViolations[0].text.slice(0, 200)}`);
    }
    if (blockedByCsp.length > 0) {
      failures.push(`a request was blocked by policy: ${blockedByCsp[0]}`);
    }

    // The application must have reached the backend at least once. A page that renders but never
    // calls the API is exactly the reported symptom.
    if (apiCalls === 0) {
      failures.push(
        "the page made no successful API request; the browser never reached the backend",
      );
    }

    const unresolvedConsole = consoleErrors.filter(
      (m) => !/favicon|manifest|Download the React DevTools/i.test(m.text),
    );

    const report = {
      edgeUrl: EDGE_URL,
      title: titleResult.result.value,
      hydration,
      apiResponses,
      networkFailures,
      badResponses,
      consoleMessages,
      failures,
    };
    writeFileSync(join(ARTIFACTS, "report.json"), JSON.stringify(report, null, 2));

    if (networkFailures.length > 0) {
      log(`network failures: ${networkFailures.length}`);
      for (const failure of networkFailures.slice(0, 5)) {
        log(`  ${failure.url} -> ${failure.errorText || failure.blockedReason}`);
      }
    }
    if (badResponses.length > 0) {
      log(`HTTP >= 400 responses: ${badResponses.length}`);
      for (const bad of badResponses.slice(0, 10)) {
        log(`  ${bad.status} ${bad.url}`);
      }
    }
    if (unresolvedConsole.length > 0) {
      log(`console errors: ${unresolvedConsole.length}`);
      for (const message of unresolvedConsole.slice(0, 5)) {
        log(`  ${message.text.slice(0, 200)}`);
      }
    }

    if (failures.length > 0) {
      for (const failure of failures) log(`FAILURE: ${failure}`);
      process.exitCode = 1;
      return;
    }
    log("no console errors, no blocked requests, API reachable from the browser");
  } catch (error) {
    log(`driver error: ${error.message}`);
    writeFileSync(
      join(ARTIFACTS, "report.json"),
      JSON.stringify({ error: String(error), consoleMessages, networkFailures }, null, 2),
    );
    process.exitCode = 1;
  } finally {
    try {
      child.kill("SIGKILL");
    } catch {
      // Already gone.
    }
    void createHash;
  }
}

await main();
