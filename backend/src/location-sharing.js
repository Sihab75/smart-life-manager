import { createHash, createHmac, randomBytes, randomUUID, timingSafeEqual } from "node:crypto";
import { Timestamp } from "firebase-admin/firestore";

const REQUEST_LIFETIME_MS = 15 * 60 * 1000;
const TOKEN_PATTERN = /^[A-Za-z0-9_-]{43}$/;

export function normalizeBangladeshPhone(value) {
  if (typeof value !== "string") return null;
  const compact = value.trim().replace(/[\s()-]/g, "");
  let localNumber;
  if (/^01[3-9]\d{8}$/.test(compact)) {
    localNumber = compact;
  } else if (/^8801[3-9]\d{8}$/.test(compact)) {
    localNumber = `0${compact.slice(3)}`;
  } else if (/^\+8801[3-9]\d{8}$/.test(compact)) {
    localNumber = `0${compact.slice(4)}`;
  } else {
    return null;
  }
  return `+880${localNumber.slice(1)}`;
}

export function hashLocationToken(token) {
  if (typeof token !== "string" || !TOKEN_PATTERN.test(token)) return null;
  return createHash("sha256").update(token, "utf8").digest("hex");
}

function hashPhone(phoneNumber, secret) {
  if (typeof secret !== "string" || Buffer.byteLength(secret, "utf8") < 32) {
    throw new Error("PHONE_LOOKUP_HMAC_SECRET must contain at least 32 bytes");
  }
  return createHmac("sha256", secret).update(phoneNumber, "utf8").digest("hex");
}

async function verifyRecipient(req, request, auth, phoneLookupSecret) {
  const idToken = req.get("x-firebase-id-token");
  if (!idToken || !auth) {
    return { status: 401, message: "Verify the recipient phone number before continuing." };
  }
  let identity;
  try {
    identity = await auth.verifyIdToken(idToken, true);
  } catch (error) {
    const code = error && typeof error === "object" && "code" in error ? error.code : null;
    return typeof code === "string" && code.startsWith("auth/")
      ? { status: 401, message: "Recipient phone verification is invalid or expired." }
      : { status: 503, message: "Recipient phone verification is temporarily unavailable." };
  }
  const phoneNumber = normalizeBangladeshPhone(identity.phone_number);
  if (!phoneNumber || typeof request.targetPhoneHash !== "string") {
    return { status: 403, message: "This request is not assigned to the verified phone." };
  }
  let verifiedHash;
  try {
    verifiedHash = hashPhone(phoneNumber, phoneLookupSecret);
  } catch {
    return { status: 503, message: "Recipient phone verification is not configured." };
  }
  const expectedHash = Buffer.from(request.targetPhoneHash, "hex");
  const actualHash = Buffer.from(verifiedHash, "hex");
  if (
    expectedHash.length !== actualHash.length ||
    !timingSafeEqual(expectedHash, actualHash) ||
    (typeof request.targetUserId === "string" &&
      !request.targetUserId.startsWith("external:") &&
      request.targetUserId !== identity.uid)
  ) {
    return { status: 403, message: "This request is not assigned to the verified phone." };
  }
  return { uid: identity.uid };
}

function dateMillis(value) {
  return value instanceof Timestamp ? value.toMillis() : 0;
}

function configuredPublicUrl(value) {
  if (!value) throw new Error("PUBLIC_BASE_URL is not configured");
  const url = new URL(value);
  const localHttp = process.env.NODE_ENV !== "production" &&
    url.protocol === "http:" &&
    ["localhost", "127.0.0.1"].includes(url.hostname);
  if (url.protocol !== "https:" && !localHttp) {
    throw new Error("PUBLIC_BASE_URL must use HTTPS");
  }
  if (url.username || url.password || url.search || url.hash || url.pathname !== "/") {
    throw new Error("PUBLIC_BASE_URL must be an origin without a path or credentials");
  }
  return url.origin;
}

function tokenFromHeader(req) {
  const match = /^Bearer\s+([A-Za-z0-9_-]{43})$/i.exec(req.get("authorization") || "");
  return match?.[1] ?? null;
}

function writeEvent(transaction, requestRef, type) {
  const eventRef = requestRef.collection("events").doc();
  transaction.set(eventRef, { type, createdAt: Timestamp.now() });
}

function releasePendingLock(transaction, firestore, request) {
  if (typeof request.dedupeKey === "string") {
    transaction.delete(firestore.collection("locationRequestLocks").doc(request.dedupeKey));
  }
}

async function requestForToken(firestore, token) {
  const tokenHash = hashLocationToken(token);
  if (!tokenHash) return null;
  const snapshot = await firestore.collection("locationRequests")
    .where("tokenHash", "==", tokenHash)
    .limit(1)
    .get();
  if (snapshot.empty) return null;
  return snapshot.docs[0];
}

async function expireIfNeeded(firestore, requestSnapshot) {
  const requestRef = requestSnapshot.ref;
  return firestore.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(requestRef);
    if (!snapshot.exists) return null;
    const request = snapshot.data();
    if (
      ["PENDING", "APPROVED"].includes(request.status) &&
      dateMillis(request.expiresAt) <= Date.now()
    ) {
      transaction.update(requestRef, { status: "EXPIRED" });
      transaction.delete(firestore.collection("locationShares").doc(requestRef.id));
      releasePendingLock(transaction, firestore, request);
      writeEvent(transaction, requestRef, "REQUEST_EXPIRED");
      return { ...request, status: "EXPIRED" };
    }
    return request;
  });
}

async function readRequesterRequest(firestore, requestRef, requesterId) {
  return firestore.runTransaction(async (transaction) => {
    const snapshot = await transaction.get(requestRef);
    if (!snapshot.exists || snapshot.data().requesterId !== requesterId) return null;
    let request = snapshot.data();
    const expired = ["PENDING", "APPROVED"].includes(request.status) &&
      dateMillis(request.expiresAt) <= Date.now();
    const locationRef = firestore.collection("locationShares").doc(requestRef.id);
    const locationSnapshot = !expired && request.status === "APPROVED"
      ? await transaction.get(locationRef)
      : null;
    if (expired) {
      transaction.update(requestRef, { status: "EXPIRED" });
      transaction.delete(locationRef);
      releasePendingLock(transaction, firestore, request);
      writeEvent(transaction, requestRef, "REQUEST_EXPIRED");
      request = { ...request, status: "EXPIRED" };
    }
    const location = request.status === "APPROVED" &&
      dateMillis(request.expiresAt) > Date.now() &&
      locationSnapshot?.exists &&
      locationSnapshot.data().active === true
      ? locationSnapshot.data()
      : null;
    return { request, location };
  });
}

function apiError(res, status, message, code) {
  return res.status(status).json({ error: message, code });
}

export function createLocationConsentRouter(
  express,
  firestore,
  { auth, phoneLookupSecret, firebaseWebConfig }
) {
  const router = express.Router();
  router.use((_req, res, next) => {
    res.set("Cache-Control", "no-store");
    res.set("Referrer-Policy", "no-referrer");
    res.set("X-Content-Type-Options", "nosniff");
    next();
  });

  router.get("/config", (_req, res) => {
    if (
      !firebaseWebConfig?.apiKey ||
      !firebaseWebConfig.authDomain ||
      !firebaseWebConfig.projectId ||
      !firebaseWebConfig.appId
    ) {
      return apiError(res, 503, "Phone verification is not configured.", "AUTH_NOT_CONFIGURED");
    }
    return res.json(firebaseWebConfig);
  });

  router.get("/", async (req, res) => {
    try {
      const token = tokenFromHeader(req);
      const snapshot = token ? await requestForToken(firestore, token) : null;
      if (!snapshot) return apiError(res, 404, "This sharing link is invalid.", "INVALID_TOKEN");
      const request = await expireIfNeeded(firestore, snapshot);
      if (!request) return apiError(res, 404, "This sharing link is invalid.", "INVALID_TOKEN");
      if (request.status === "EXPIRED") {
        return res.status(410).json({ status: "EXPIRED", message: "This location request has expired." });
      }
      if (request.status === "DECLINED") {
        return res.status(410).json({ status: "DECLINED", message: "This location request was declined." });
      }
      if (request.status === "REVOKED") {
        return res.status(410).json({ status: "REVOKED", message: "Location sharing has been stopped." });
      }
      const response = {
        requestId: snapshot.id,
        requesterName: request.requesterName,
        createdAt: dateMillis(request.createdAt),
        expiresAt: dateMillis(request.expiresAt),
        status: request.status
      };
      if (request.status === "APPROVED") {
        const location = await firestore.collection("locationShares").doc(snapshot.id).get();
        if (location.exists && location.data().active === true) {
          response.lastUpdated = dateMillis(location.data().createdAt);
          response.accuracy = location.data().accuracy;
        }
      }
      return res.json(response);
    } catch {
      return apiError(res, 503, "The location service is temporarily unavailable.", "SERVICE_UNAVAILABLE");
    }
  });

  router.post("/decline", async (req, res) => {
    try {
      const token = tokenFromHeader(req);
      const snapshot = token ? await requestForToken(firestore, token) : null;
      if (!snapshot) return apiError(res, 404, "This sharing link is invalid.", "INVALID_TOKEN");
      const recipient = await verifyRecipient(req, snapshot.data(), auth, phoneLookupSecret);
      if (!recipient.uid) {
        return apiError(res, recipient.status, recipient.message, "RECIPIENT_NOT_VERIFIED");
      }
      const result = await firestore.runTransaction(async (transaction) => {
        const current = await transaction.get(snapshot.ref);
        if (!current.exists) return "INVALID_TOKEN";
        const request = current.data();
        if (dateMillis(request.expiresAt) <= Date.now()) {
          if (["PENDING", "APPROVED"].includes(request.status)) {
            transaction.update(snapshot.ref, { status: "EXPIRED" });
            transaction.delete(firestore.collection("locationShares").doc(snapshot.id));
            releasePendingLock(transaction, firestore, request);
            writeEvent(transaction, snapshot.ref, "REQUEST_EXPIRED");
          }
          return "EXPIRED";
        }
        if (request.status !== "PENDING") return request.status;
        transaction.update(snapshot.ref, {
          status: "DECLINED",
          targetUserId: recipient.uid
        });
        releasePendingLock(transaction, firestore, request);
        writeEvent(transaction, snapshot.ref, "REQUEST_DECLINED");
        return "DECLINED";
      });
      if (result === "DECLINED") return res.json({ status: result });
      if (result === "EXPIRED") return apiError(res, 410, "This location request has expired.", result);
      if (result === "INVALID_TOKEN") return apiError(res, 404, "This sharing link is invalid.", result);
      return apiError(res, 409, "This request can no longer be declined.", result);
    } catch {
      return apiError(res, 503, "The location service is temporarily unavailable.", "SERVICE_UNAVAILABLE");
    }
  });

  router.post("/share", async (req, res) => {
    const { latitude, longitude, accuracy } = req.body ?? {};
    if (
      typeof latitude !== "number" || !Number.isFinite(latitude) || latitude < -90 || latitude > 90 ||
      typeof longitude !== "number" || !Number.isFinite(longitude) || longitude < -180 || longitude > 180 ||
      typeof accuracy !== "number" || !Number.isFinite(accuracy) || accuracy < 0 || accuracy > 100000
    ) {
      return apiError(res, 400, "The browser did not provide a valid location.", "INVALID_LOCATION");
    }
    try {
      const token = tokenFromHeader(req);
      const snapshot = token ? await requestForToken(firestore, token) : null;
      if (!snapshot) return apiError(res, 404, "This sharing link is invalid.", "INVALID_TOKEN");
      const recipient = await verifyRecipient(req, snapshot.data(), auth, phoneLookupSecret);
      if (!recipient.uid) {
        return apiError(res, recipient.status, recipient.message, "RECIPIENT_NOT_VERIFIED");
      }
      const result = await firestore.runTransaction(async (transaction) => {
        const current = await transaction.get(snapshot.ref);
        if (!current.exists) return "INVALID_TOKEN";
        const request = current.data();
        if (dateMillis(request.expiresAt) <= Date.now()) {
          if (["PENDING", "APPROVED"].includes(request.status)) {
            transaction.update(snapshot.ref, { status: "EXPIRED" });
            transaction.delete(firestore.collection("locationShares").doc(snapshot.id));
            releasePendingLock(transaction, firestore, request);
            writeEvent(transaction, snapshot.ref, "REQUEST_EXPIRED");
          }
          return "EXPIRED";
        }
        if (!["PENDING", "APPROVED"].includes(request.status)) return request.status;
        if (request.status === "PENDING") {
          transaction.update(snapshot.ref, {
            status: "APPROVED",
            targetUserId: recipient.uid
          });
          releasePendingLock(transaction, firestore, request);
          writeEvent(transaction, snapshot.ref, "REQUEST_APPROVED");
          writeEvent(transaction, snapshot.ref, "SHARING_STARTED");
        }
        transaction.set(firestore.collection("locationShares").doc(snapshot.id), {
          shareId: snapshot.id,
          requestId: snapshot.id,
          ownerId: recipient.uid,
          viewerId: request.requesterId,
          latitude,
          longitude,
          accuracy,
          createdAt: Timestamp.now(),
          expiresAt: request.expiresAt,
          active: true
        });
        return "APPROVED";
      });
      if (result === "APPROVED") return res.json({ status: result });
      if (result === "EXPIRED") return apiError(res, 410, "This location request has expired.", result);
      if (result === "INVALID_TOKEN") return apiError(res, 404, "This sharing link is invalid.", result);
      if (result === "DECLINED") return apiError(res, 409, "This request was declined.", result);
      return apiError(res, 409, "Location sharing has been stopped.", result);
    } catch {
      return apiError(res, 503, "The location could not be shared. Please try again.", "SERVICE_UNAVAILABLE");
    }
  });

  router.post("/stop", async (req, res) => {
    try {
      const token = tokenFromHeader(req);
      const snapshot = token ? await requestForToken(firestore, token) : null;
      if (!snapshot) return apiError(res, 404, "This sharing link is invalid.", "INVALID_TOKEN");
      const recipient = await verifyRecipient(req, snapshot.data(), auth, phoneLookupSecret);
      if (!recipient.uid) {
        return apiError(res, recipient.status, recipient.message, "RECIPIENT_NOT_VERIFIED");
      }
      const result = await firestore.runTransaction(async (transaction) => {
        const current = await transaction.get(snapshot.ref);
        if (!current.exists) return "INVALID_TOKEN";
        const request = current.data();
        if (dateMillis(request.expiresAt) <= Date.now()) {
          if (["PENDING", "APPROVED"].includes(request.status)) {
            transaction.update(snapshot.ref, { status: "EXPIRED" });
            transaction.delete(firestore.collection("locationShares").doc(snapshot.id));
            releasePendingLock(transaction, firestore, request);
            writeEvent(transaction, snapshot.ref, "REQUEST_EXPIRED");
          }
          return "EXPIRED";
        }
        if (!["PENDING", "APPROVED"].includes(request.status)) return request.status;
        transaction.update(snapshot.ref, {
          status: "REVOKED",
          targetUserId: recipient.uid
        });
        transaction.delete(firestore.collection("locationShares").doc(snapshot.id));
        releasePendingLock(transaction, firestore, request);
        writeEvent(
          transaction,
          snapshot.ref,
          request.status === "APPROVED" ? "SHARING_STOPPED" : "REQUEST_REVOKED"
        );
        return "REVOKED";
      });
      if (result === "REVOKED") return res.json({ status: result });
      if (result === "EXPIRED") return apiError(res, 410, "This location request has expired.", result);
      if (result === "INVALID_TOKEN") return apiError(res, 404, "This sharing link is invalid.", result);
      return apiError(res, 409, "This request can no longer be stopped.", result);
    } catch {
      return apiError(res, 503, "The location service is temporarily unavailable.", "SERVICE_UNAVAILABLE");
    }
  });
  return router;
}

export function createLocationApiRouter(
  express,
  firestore,
  { publicBaseUrl, phoneLookupSecret, messaging }
) {
  const router = express.Router();

  router.post("/requests", async (req, res) => {
    const phoneNumber = normalizeBangladeshPhone(req.body?.phoneNumber);
    if (!phoneNumber) {
      return apiError(res, 400, "Enter a valid Bangladesh mobile number.", "INVALID_PHONE");
    }
    let baseUrl;
    let targetPhoneHash;
    let dedupeKey;
    try {
      baseUrl = configuredPublicUrl(publicBaseUrl);
      targetPhoneHash = hashPhone(phoneNumber, phoneLookupSecret);
      dedupeKey = hashPhone(`${req.firebaseUser.uid}:${phoneNumber}`, phoneLookupSecret);
    } catch {
      return apiError(res, 503, "Location sharing is not configured on the server.", "SERVICE_UNAVAILABLE");
    }

    try {
      const requesterId = req.firebaseUser.uid;
      const requesterProfile = await firestore.collection("users").doc(requesterId).get();
      const requesterPhone = normalizeBangladeshPhone(
        req.firebaseUser.phone_number ?? requesterProfile.data()?.phoneNumber
      );
      if (requesterPhone && requesterPhone === phoneNumber) {
        return apiError(res, 400, "You cannot request your own location.", "OWN_PHONE");
      }
      const matchingUser = await firestore.collection("users")
        .where("phoneNumber", "==", phoneNumber)
        .limit(1)
        .get();
      const matchingProfile = matchingUser.empty ? null : matchingUser.docs[0].data();
      const targetUserId = matchingUser.empty ? null : matchingUser.docs[0].id;
      const targetName = String(matchingProfile?.displayName || "Location recipient").trim().slice(0, 80);
      const requestId = randomUUID();
      const token = randomBytes(32).toString("base64url");
      const tokenHash = createHash("sha256").update(token, "utf8").digest("hex");
      const now = Timestamp.now();
      const expiresAt = Timestamp.fromMillis(now.toMillis() + REQUEST_LIFETIME_MS);
      const requestRef = firestore.collection("locationRequests").doc(requestId);
      const lockRef = firestore.collection("locationRequestLocks").doc(dedupeKey);
      const nameFromProfile = requesterProfile.data()?.displayName;
      const requesterName = String(nameFromProfile || req.firebaseUser.name || "A Smart Life Manager user")
        .trim()
        .slice(0, 80);

      const result = await firestore.runTransaction(async (transaction) => {
        const lock = await transaction.get(lockRef);
        if (lock.exists) {
          const lockedRequestId = lock.data().requestId;
          const existingRequest = typeof lockedRequestId === "string"
            ? await transaction.get(firestore.collection("locationRequests").doc(lockedRequestId))
            : null;
          if (
            existingRequest?.exists &&
            existingRequest.data().status === "PENDING" &&
            dateMillis(existingRequest.data().expiresAt) > Date.now()
          ) return false;
        }

        transaction.create(requestRef, {
          requestId,
          requesterId,
          targetUserId: targetUserId || `external:${requestId}`,
          targetName,
          targetPhoneHash,
          dedupeKey,
          tokenHash,
          requesterName,
          createdAt: now,
          expiresAt,
          status: "PENDING"
        });
        transaction.set(lockRef, { requestId, expiresAt });
        writeEvent(transaction, requestRef, "REQUEST_CREATED");
        return true;
      });
      if (!result) {
        return apiError(res, 409, "A location request to this number is already pending.", "DUPLICATE_PENDING");
      }
      const fcmToken = matchingProfile?.fcmToken;
      if (targetUserId && typeof fcmToken === "string" && fcmToken.length > 0 && messaging) {
        try {
          await messaging.send({
            token: fcmToken,
            data: {
              type: "LOCATION_REQUEST",
              requestId,
              shareUrl: `${baseUrl}/location-share#token=${token}`
            },
            android: { priority: "high" }
          });
        } catch {
          console.warn("Location request push notification could not be delivered.");
        }
      }
      return res.status(201).json({
        requestId,
        shareUrl: `${baseUrl}/location-share#token=${token}`,
        createdAt: now.toMillis(),
        expiresAt: expiresAt.toMillis(),
        status: "PENDING"
      });
    } catch {
      return apiError(res, 503, "The location request could not be sent. Please try again.", "SERVICE_UNAVAILABLE");
    }
  });

  router.get("/requests", async (req, res) => {
    try {
      const requests = await firestore.collection("locationRequests")
        .where("requesterId", "==", req.firebaseUser.uid)
        .orderBy("createdAt", "desc")
        .limit(50)
        .get();
      const items = [];
      for (const snapshot of requests.docs) {
        const authorized = await readRequesterRequest(firestore, snapshot.ref, req.firebaseUser.uid);
        if (!authorized) continue;
        const { request, location } = authorized;
        const item = {
          requestId: snapshot.id,
          requesterId: request.requesterId,
          targetUserId: request.targetUserId,
          targetName: request.targetName || "Location recipient",
          requesterName: request.requesterName,
          createdAt: dateMillis(request.createdAt),
          expiresAt: dateMillis(request.expiresAt),
          status: request.status,
          location: null
        };
        if (location) {
          item.location = {
            latitude: location.latitude,
            longitude: location.longitude,
            accuracy: location.accuracy,
            updatedAt: dateMillis(location.createdAt)
          };
        }
        items.push(item);
      }
      items.sort((left, right) => right.createdAt - left.createdAt);
      return res.json({ requests: items });
    } catch {
      return apiError(res, 503, "Location requests could not be loaded.", "SERVICE_UNAVAILABLE");
    }
  });

  router.get("/requests/:requestId/location", async (req, res) => {
    try {
      const requestRef = firestore.collection("locationRequests").doc(req.params.requestId);
      const authorized = await readRequesterRequest(firestore, requestRef, req.firebaseUser.uid);
      if (!authorized) {
        return apiError(res, 404, "This location request was not found.", "NOT_FOUND");
      }
      const { request, location } = authorized;
      if (!request || request.status !== "APPROVED" || dateMillis(request.expiresAt) <= Date.now()) {
        return apiError(res, 403, "There is no active shared location for this request.", "NOT_ACTIVE");
      }
      if (!location) {
        return apiError(res, 404, "Location is not available yet.", "LOCATION_UNAVAILABLE");
      }
      return res.json({
        requestId: requestRef.id,
        expiresAt: dateMillis(request.expiresAt),
        location: {
          latitude: location.latitude,
          longitude: location.longitude,
          accuracy: location.accuracy,
          updatedAt: dateMillis(location.createdAt)
        }
      });
    } catch {
      return apiError(res, 503, "The shared location could not be loaded.", "SERVICE_UNAVAILABLE");
    }
  });

  router.post("/requests/:requestId/stop", async (req, res) => {
    try {
      const requestRef = firestore.collection("locationRequests").doc(req.params.requestId);
      const result = await firestore.runTransaction(async (transaction) => {
        const snapshot = await transaction.get(requestRef);
        if (!snapshot.exists || snapshot.data().requesterId !== req.firebaseUser.uid) return "NOT_FOUND";
        const request = snapshot.data();
        if (dateMillis(request.expiresAt) <= Date.now()) {
          if (["PENDING", "APPROVED"].includes(request.status)) {
            transaction.update(requestRef, { status: "EXPIRED" });
            transaction.delete(firestore.collection("locationShares").doc(requestRef.id));
            releasePendingLock(transaction, firestore, request);
            writeEvent(transaction, requestRef, "REQUEST_EXPIRED");
          }
          return "EXPIRED";
        }
        if (!["PENDING", "APPROVED"].includes(request.status)) return request.status;
        transaction.update(requestRef, { status: "REVOKED" });
        transaction.delete(firestore.collection("locationShares").doc(requestRef.id));
        releasePendingLock(transaction, firestore, request);
        writeEvent(transaction, requestRef, "SHARING_STOPPED");
        return "REVOKED";
      });
      if (result === "REVOKED") return res.json({ status: result });
      if (result === "EXPIRED") return apiError(res, 410, "This location request has expired.", result);
      if (result === "NOT_FOUND") return apiError(res, 404, "This location request was not found.", result);
      return apiError(res, 409, "This request is no longer active.", result);
    } catch {
      return apiError(res, 503, "Location sharing could not be stopped.", "SERVICE_UNAVAILABLE");
    }
  });
  return router;
}
