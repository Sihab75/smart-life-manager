const state = {
  token: null,
  request: null,
  firebase: null,
  firebaseIdToken: null,
  watchId: null,
  lastSentAt: 0,
  expiryTimer: null
};

const byId = (id) => document.getElementById(id);

function showNotice(message, kind = "") {
  const notice = byId("notice");
  notice.textContent = message;
  notice.dataset.kind = kind;
}

function phoneAuthErrorMessage(error, fallback) {
  const code = String(error?.code || "");
  if (code.includes("auth/invalid-phone-number")) return "Enter a valid Bangladesh phone number.";
  if (code.includes("auth/invalid-verification-code")) return "That verification code is incorrect.";
  if (code.includes("auth/code-expired")) return "That code expired. Request a new verification code.";
  if (code.includes("auth/too-many-requests")) return "Too many verification attempts. Please wait before trying again.";
  if (code.includes("auth/captcha-check-failed")) return "The browser verification failed. Refresh and try again.";
  if (String(error?.message || "").includes("not configured")) {
    return "Phone verification is not configured. No location can be shared.";
  }
  if (!error?.code && /fetch|network/i.test(String(error?.message || ""))) {
    return "Phone verification is unavailable because of a network error. Check your connection.";
  }
  return fallback;
}

function setVisible(id, visible) {
  byId(id).hidden = !visible;
}

function formatDate(timestamp) {
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" })
    .format(new Date(timestamp));
}

function formatRemaining(timestamp) {
  const seconds = Math.max(0, Math.floor((timestamp - Date.now()) / 1000));
  if (seconds === 0) return "Expired";
  return `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, "0")}`;
}

async function consentRequest(path, body) {
  const headers = {
    Authorization: `Bearer ${state.token}`,
    ...(body === undefined ? {} : { "Content-Type": "application/json" }),
    ...(state.firebaseIdToken ? { "X-Firebase-ID-Token": state.firebaseIdToken } : {})
  };
  const response = await fetch(`/api/location-consent${path}`, {
    method: body === undefined ? "GET" : "POST",
    headers,
    cache: "no-store",
    body: body === undefined ? undefined : JSON.stringify(body)
  });
  const result = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(result.message || result.error || "The request could not be completed.");
    error.code = result.code || result.status;
    error.status = response.status;
    throw error;
  }
  return result;
}

function normalizeBangladeshPhone(value) {
  const compact = value.trim().replace(/[\s()-]/g, "");
  let local;
  if (/^01[3-9]\d{8}$/.test(compact)) local = compact;
  else if (/^8801[3-9]\d{8}$/.test(compact)) local = `0${compact.slice(3)}`;
  else if (/^\+8801[3-9]\d{8}$/.test(compact)) local = `0${compact.slice(4)}`;
  else return null;
  return `+880${local.slice(1)}`;
}

function showVerifiedActions() {
  setVisible("phone-verification", false);
  setVisible("verified-phone-label", true);
  if (state.request.status === "PENDING") {
    setVisible("consent-controls", true);
    setVisible("share-button", true);
    setVisible("decline-button", true);
  } else if (state.request.status === "APPROVED") {
    setVisible("active-controls", true);
    setVisible("update-button", true);
    setVisible("stop-button", true);
  }
}

async function sendVerificationCode() {
  const phoneNumber = normalizeBangladeshPhone(byId("recipient-phone").value);
  if (!phoneNumber) {
    showNotice("Enter the Bangladesh phone number that received this request.", "error");
    return;
  }
  byId("send-code-button").disabled = true;
  try {
    if (!state.firebase) throw new Error("Phone verification is unavailable.");
    await state.firebase.sendCode(phoneNumber);
    showNotice("A verification code was sent by Firebase. Verification does not share your location.");
    setVisible("verification-code-controls", true);
    byId("send-code-button").disabled = false;
  } catch (error) {
    showNotice(phoneAuthErrorMessage(error, "A verification code could not be sent. Check the number and try again."), "error");
    byId("send-code-button").disabled = false;
  }
}

async function confirmVerificationCode() {
  const code = byId("verification-code").value.trim();
  if (!/^\d{4,10}$/.test(code)) {
    showNotice("Enter the verification code sent to your phone.", "error");
    return;
  }
  byId("verify-code-button").disabled = true;
  try {
    if (!state.firebase) throw new Error("Phone verification is unavailable.");
    state.firebaseIdToken = await state.firebase.confirmCode(code);
    showVerifiedActions();
    showNotice("Phone verified. Your location has not been accessed. Choose Share my location to continue.", "success");
  } catch (error) {
    showNotice(phoneAuthErrorMessage(error, "Phone verification failed. Check the code and try again."), "error");
    byId("verify-code-button").disabled = false;
  }
}

function stopBrowserUpdates() {
  if (state.watchId !== null && navigator.geolocation) {
    navigator.geolocation.clearWatch(state.watchId);
    state.watchId = null;
  }
}

function showTerminalState(message) {
  stopBrowserUpdates();
  if (state.expiryTimer !== null) {
    window.clearInterval(state.expiryTimer);
    state.expiryTimer = null;
  }
  setVisible("consent-controls", false);
  setVisible("active-controls", false);
  setVisible("phone-verification", false);
  showNotice(message, "error");
}

function updateCountdown() {
  if (!state.request) return;
  const remaining = formatRemaining(state.request.expiresAt);
  byId("countdown").textContent = remaining;
  byId("active-countdown").textContent = remaining;
  if (remaining === "Expired") {
    stopBrowserUpdates();
    showTerminalState("This location request has expired. Your location is no longer available to the requester.");
  }
}

async function handleLocationError(error) {
  if (error.code === 1) {
    let revokeFailed = false;
    if (state.request?.status === "APPROVED") {
      try {
        await consentRequest("/stop", {});
        state.request.status = "REVOKED";
      } catch {
        revokeFailed = true;
      }
    }
    if (state.request?.status === "REVOKED") {
      showTerminalState("Location sharing was denied and stopped. No new coordinates were uploaded.");
    } else if (revokeFailed) {
      showNotice("Location sharing was denied. No new coordinates were uploaded, but the previous share could not be stopped because the network is unavailable. Use Stop sharing when you reconnect.", "error");
    } else {
      showNotice("Location sharing was denied. No coordinates were uploaded. You can allow location access in your browser settings and try again.", "error");
    }
  } else if (error.code === 2) {
    showNotice("Your location is unavailable. Check that location services are on, then try again.", "error");
  } else if (error.code === 3) {
    showNotice("Finding your location took too long. Please try again.", "error");
  } else {
    showNotice("The browser could not access your location. No coordinates were uploaded.", "error");
  }
  byId("share-button").disabled = false;
}

async function uploadPosition(position) {
  if (!state.request || Date.now() >= state.request.expiresAt) return;
  const now = Date.now();
  if (now - state.lastSentAt < 10000) return;
  state.lastSentAt = now;
  const location = position.coords;
  try {
    await consentRequest("/share", {
      latitude: location.latitude,
      longitude: location.longitude,
      accuracy: location.accuracy
    });
    state.request.status = "APPROVED";
    showVerifiedActions();
    setVisible("active-controls", true);
    setVisible("consent-controls", false);
    showNotice("Your latest location is shared only with the requester named above.", "success");
    byId("last-updated").textContent = "Just now";
    byId("accuracy").textContent = `±${Math.round(location.accuracy)} m`;
    byId("share-button").disabled = false;
  } catch (error) {
    showTerminalState(error.status === 410
      ? "This location request has expired. No further location updates can be shared."
      : "Your location update could not be sent. Check your connection; sharing will retry while this page stays open.");
  }
}

function startWatching() {
  if (!navigator.geolocation || state.watchId !== null) return;
  try {
    state.watchId = navigator.geolocation.watchPosition(
      uploadPosition,
      handleLocationError,
      { enableHighAccuracy: true, maximumAge: 5000, timeout: 20000 }
    );
  } catch {
    showNotice("Your current location was shared, but live updates could not start. You can update manually or stop sharing.", "error");
  }
}

async function shareLocation() {
  if (!window.isSecureContext) {
    showNotice("Location sharing requires a secure HTTPS connection.", "error");
    return;
  }
  if (!navigator.geolocation) {
    showNotice("This browser does not support location sharing.", "error");
    return;
  }
  byId("share-button").disabled = true;
  showNotice("Waiting for your browser's location permission. Nothing is shared unless you allow it.");
  try {
    navigator.geolocation.getCurrentPosition(
      async (position) => {
        await uploadPosition(position);
        if (state.request && Date.now() < state.request.expiresAt && !document.hidden) startWatching();
      },
      handleLocationError,
      { enableHighAccuracy: true, maximumAge: 0, timeout: 20000 }
    );
  } catch {
    handleLocationError({ code: 0 });
  }
}

async function declineRequest() {
  byId("decline-button").disabled = true;
  try {
    await consentRequest("/decline", {});
    showTerminalState("You declined this request. No location was shared.");
  } catch (error) {
    showNotice(error.status === 410
      ? "This location request has expired."
      : "The request could not be declined right now. Please try again.", "error");
    byId("decline-button").disabled = false;
  }
}

async function stopSharing() {
  byId("stop-button").disabled = true;
  try {
    await consentRequest("/stop", {});
    showTerminalState("Location sharing stopped. The requester can no longer access your location.");
  } catch (error) {
    showNotice(error.status === 410
      ? "This request has expired. Location access is no longer available."
      : "Sharing could not be stopped because the connection failed. Please retry.", "error");
    byId("stop-button").disabled = false;
  }
}

async function loadRequest() {
  try {
    const request = await consentRequest("/", undefined);
    state.request = request;
    byId("requester-name").textContent = request.requesterName || "Smart Life Manager user";
    byId("request-time").textContent = formatDate(request.createdAt);
    setVisible("request-details", true);
    setVisible("phone-verification", true);
    if (request.status === "APPROVED") {
      setVisible("active-controls", true);
      byId("last-updated").textContent = request.lastUpdated
        ? `${Math.max(0, Math.floor((Date.now() - request.lastUpdated) / 1000))} seconds ago`
        : "Not available";
      byId("accuracy").textContent = Number.isFinite(request.accuracy)
        ? `±${Math.round(request.accuracy)} m`
        : "Not available";
      byId("active-copy").textContent = "Location updates pause when this page is closed, hidden, or suspended. Choose Update location to resume.";
      showNotice("This request has your approval. No location is collected automatically when this page opens.", "success");
    } else {
      setVisible("consent-controls", true);
      showNotice("Review the request. Your browser will ask for location access only after you press Share my location.");
    }
    updateCountdown();
    state.expiryTimer = window.setInterval(updateCountdown, 1000);
  } catch (error) {
    showTerminalState(error.status
      ? error.message || "This location request is invalid or unavailable."
      : "Could not load this request. Check your connection and open the link again.");
  }
}

async function initialize() {
  const fragmentToken = new URLSearchParams(window.location.hash.slice(1)).get("token");
  state.token = fragmentToken;
  if (!state.token || !/^[A-Za-z0-9_-]{43}$/.test(state.token)) {
    showTerminalState("This sharing link is invalid. Ask the requester to send a new link.");
    return;
  }
  history.replaceState(null, "", `${window.location.pathname}${window.location.search}`);
  byId("share-button").addEventListener("click", shareLocation);
  byId("update-button").addEventListener("click", shareLocation);
  byId("decline-button").addEventListener("click", declineRequest);
  byId("stop-button").addEventListener("click", stopSharing);
  byId("send-code-button").addEventListener("click", sendVerificationCode);
  byId("verify-code-button").addEventListener("click", confirmVerificationCode);
  await loadRequest();
  try {
    const firebaseClient = await window.locationFirebase?.ready;
    if (!firebaseClient || firebaseClient.initializationError) {
      throw firebaseClient?.initializationError || new Error("Phone verification is unavailable.");
    }
    state.firebase = firebaseClient;
  } catch {
    byId("send-code-button").disabled = true;
    showNotice("Phone verification is unavailable. No location can be shared. Check the service configuration and try again.", "error");
  }
}

window.addEventListener("pagehide", stopBrowserUpdates);
document.addEventListener("visibilitychange", () => {
  if (document.hidden) {
    stopBrowserUpdates();
  } else if (state.request?.status === "APPROVED") {
    showNotice("Location updates are paused. Choose Update location to resume sharing.", "success");
  }
});
initialize();
