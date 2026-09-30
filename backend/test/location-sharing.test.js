import test from "node:test";
import assert from "node:assert/strict";
import { randomBytes } from "node:crypto";
import express from "express";
import { once } from "node:events";
import { readFile } from "node:fs/promises";
import { runInNewContext } from "node:vm";
import { Timestamp } from "firebase-admin/firestore";
import { hashLocationToken, normalizeBangladeshPhone } from "../src/location-sharing.js";
import { createLocationApiRouter, createLocationConsentRouter } from "../src/location-sharing.js";

test("normalizes supported Bangladesh mobile formats to one canonical form", () => {
  assert.equal(normalizeBangladeshPhone("01712345678"), "+8801712345678");
  assert.equal(normalizeBangladeshPhone("+880 1712-345678"), "+8801712345678");
  assert.equal(normalizeBangladeshPhone("8801712345678"), "+8801712345678");
});

test("rejects malformed and non-Bangladesh phone numbers", () => {
  for (const value of ["", "0171234567", "01212345678", "+14155550123", "017123456789"]) {
    assert.equal(normalizeBangladeshPhone(value), null);
  }
  assert.equal(normalizeBangladeshPhone(null), null);
});

test("only accepts a full 256-bit URL-safe bearer token and stores its digest", () => {
  const token = randomBytes(32).toString("base64url");
  const digest = hashLocationToken(token);
  assert.match(token, /^[A-Za-z0-9_-]{43}$/);
  assert.match(digest, /^[a-f0-9]{64}$/);
  assert.notEqual(digest, token);
  assert.equal(hashLocationToken(token), digest);
  assert.equal(hashLocationToken("predictable-user-id"), null);
});

class Snapshot {
  constructor(ref, data) {
    this.ref = ref;
    this.id = ref.id;
    this.exists = data !== undefined;
    this.value = data;
  }

  data() {
    return this.value === undefined ? undefined : { ...this.value };
  }
}

class FakeQuery {
  constructor(store, path, filters = [], maximum = Infinity, ordering = null) {
    this.store = store;
    this.path = path;
    this.filters = filters;
    this.maximum = maximum;
    this.ordering = ordering;
  }

  where(field, operator, value) {
    assert.equal(operator, "==");
    return new FakeQuery(this.store, this.path, [...this.filters, [field, value]], this.maximum, this.ordering);
  }

  orderBy(field, direction = "asc") {
    return new FakeQuery(this.store, this.path, this.filters, this.maximum, [field, direction]);
  }

  limit(maximum) {
    return new FakeQuery(this.store, this.path, this.filters, maximum, this.ordering);
  }

  async get() {
    const prefix = `${this.path}/`;
    const docs = [];
    for (const [path, value] of this.store.documents) {
      if (!path.startsWith(prefix) || path.slice(prefix.length).includes("/")) continue;
      if (!this.filters.every(([field, expected]) => value[field] === expected)) continue;
      docs.push(new Snapshot(new FakeDocument(this.store, this.path, path.slice(prefix.length)), value));
    }
    if (this.ordering) {
      const [field, direction] = this.ordering;
      docs.sort((left, right) => {
        const leftValue = left.data()[field]?.toMillis?.() ?? left.data()[field];
        const rightValue = right.data()[field]?.toMillis?.() ?? right.data()[field];
        return (direction === "desc" ? -1 : 1) * (leftValue - rightValue);
      });
    }
    docs.splice(this.maximum);
    return { docs, empty: docs.length === 0 };
  }
}

class FakeDocument {
  constructor(store, collectionPath, id) {
    this.store = store;
    this.collectionPath = collectionPath;
    this.id = id;
    this.path = `${collectionPath}/${id}`;
  }

  collection(name) {
    return new FakeCollection(this.store, `${this.path}/${name}`);
  }

  async get() {
    return new Snapshot(this, this.store.documents.get(this.path));
  }
}

class FakeCollection extends FakeQuery {
  constructor(store, path, filters = [], maximum = Infinity, ordering = null) {
    super(store, path, filters, maximum, ordering);
  }

  doc(id = randomBytes(16).toString("hex")) {
    return new FakeDocument(this.store, this.path, id);
  }
}

class FakeTransaction {
  constructor(store) {
    this.store = store;
    this.writes = [];
  }

  async get(reference) {
    if (reference instanceof FakeQuery) return reference.get();
    return reference.get();
  }

  create(reference, value) {
    this.writes.push(["create", reference.path, value]);
  }

  set(reference, value) {
    this.writes.push(["set", reference.path, value]);
  }

  update(reference, value) {
    this.writes.push(["update", reference.path, value]);
  }

  delete(reference) {
    this.writes.push(["delete", reference.path]);
  }
}

class FakeFirestore {
  constructor() {
    this.documents = new Map();
  }

  collection(path) {
    return new FakeCollection(this, path);
  }

  seed(path, value) {
    this.documents.set(path, value);
  }

  async runTransaction(work) {
    const transaction = new FakeTransaction(this);
    const result = await work(transaction);
    for (const [operation, path, value] of transaction.writes) {
      if (operation === "delete") this.documents.delete(path);
      else if (operation === "update") {
        this.documents.set(path, { ...this.documents.get(path), ...value });
      } else if (operation === "create") {
        if (this.documents.has(path)) throw new Error("Document already exists");
        this.documents.set(path, value);
      } else {
        this.documents.set(path, value);
      }
    }
    return result;
  }
}

test("consent is required, requester-only reads are enforced, and revoke removes access", async (t) => {
  const firestore = new FakeFirestore();
  firestore.seed("users/requester", {
    displayName: "Requesting User",
    phoneNumber: "+8801712345678"
  });
  firestore.seed("users/recipient", {
    displayName: "Recipient",
    phoneNumber: "+8801812345678",
    fcmToken: "test-device-token"
  });
  const sentMessages = [];
  const app = express();
  app.use(express.json());
  const auth = {
    async verifyIdToken(token) {
      if (token === "recipient-phone") {
        return { uid: "recipient", phone_number: "+8801812345678" };
      }
      if (token === "wrong-recipient-phone") {
        return { uid: "stranger", phone_number: "+8801912345678" };
      }
      throw Object.assign(new Error("invalid token"), { code: "auth/invalid-id-token" });
    }
  };
  app.use("/api/location-consent", createLocationConsentRouter(express, firestore, {
    auth,
    phoneLookupSecret: "test-secret-with-more-than-32-bytes",
    firebaseWebConfig: {
      apiKey: "public-test-key",
      authDomain: "test.firebaseapp.com",
      projectId: "test-project",
      appId: "test-app"
    }
  }));
  app.use("/api/location", (req, res, next) => {
    const auth = req.get("authorization");
    if (auth === "Bearer requester") {
      req.firebaseUser = { uid: "requester", phone_number: "+8801712345678" };
      return next();
    }
    if (auth === "Bearer stranger") {
      req.firebaseUser = { uid: "stranger", phone_number: "+8801812345678" };
      return next();
    }
    return res.status(401).json({ error: "Firebase sign-in is required" });
  }, createLocationApiRouter(express, firestore, {
    publicBaseUrl: "https://location.example",
    phoneLookupSecret: "test-secret-with-more-than-32-bytes",
    messaging: { send: async (message) => { sentMessages.push(message); return "message-id"; } }
  }));
  const server = app.listen(0, "127.0.0.1");
  await once(server, "listening");
  t.after(() => new Promise((resolve, reject) => {
    server.close((error) => error ? reject(error) : resolve());
  }));
  const origin = `http://127.0.0.1:${server.address().port}`;
  const request = (path, { method = "GET", token, phoneToken, body } = {}) => fetch(`${origin}${path}`, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(phoneToken ? { "X-Firebase-ID-Token": phoneToken } : {}),
      ...(body === undefined ? {} : { "Content-Type": "application/json" })
    },
    body: body === undefined ? undefined : JSON.stringify(body)
  });

  const unauthenticated = await request("/api/location/requests");
  assert.equal(unauthenticated.status, 401);
  const selfRequest = await request("/api/location/requests", {
    method: "POST",
    token: "requester",
    body: { phoneNumber: "+8801712345678" }
  });
  assert.equal(selfRequest.status, 400);
  const createdResponse = await request("/api/location/requests", {
    method: "POST",
    token: "requester",
    body: { phoneNumber: "01812345678" }
  });
  assert.equal(createdResponse.status, 201);
  const created = await createdResponse.json();
  const token = new URL(created.shareUrl).hash.slice("#token=".length);
  const requestRef = firestore.collection("locationRequests").doc(created.requestId);
  const storedRequest = (await requestRef.get()).data();
  assert.equal(storedRequest.status, "PENDING");
  assert.equal(storedRequest.targetUserId, "recipient");
  assert.equal(storedRequest.targetName, "Recipient");
  assert.equal(storedRequest.targetPhoneHash.length, 64);
  assert.equal(storedRequest.tokenHash, hashLocationToken(token));
  assert.equal(JSON.stringify(storedRequest).includes(token), false);
  assert.equal(sentMessages.length, 1);
  assert.equal(sentMessages[0].data.type, "LOCATION_REQUEST");
  assert.equal(sentMessages[0].data.shareUrl, created.shareUrl);
  assert.equal("latitude" in sentMessages[0].data, false);
  assert.equal(firestore.documents.has(`locationShares/${created.requestId}`), false);

  const prematureRead = await request(
    `/api/location/requests/${created.requestId}/location`,
    { token: "requester" }
  );
  assert.equal(prematureRead.status, 403);
  const invalidLocation = await request("/api/location-consent/share", {
    method: "POST",
    token,
    body: { latitude: 91, longitude: 0, accuracy: 1 }
  });
  assert.equal(invalidLocation.status, 400);
  assert.equal(firestore.documents.has(`locationShares/${created.requestId}`), false);
  const requesterAttempt = await request("/api/location-consent/share", {
    method: "POST",
    token,
    phoneToken: "requester",
    body: { latitude: 23.8103, longitude: 90.4125, accuracy: 15 }
  });
  assert.equal(requesterAttempt.status, 401);
  const linkOnlyAttempt = await request("/api/location-consent/share", {
    method: "POST",
    token,
    body: { latitude: 23.8103, longitude: 90.4125, accuracy: 15 }
  });
  assert.equal(linkOnlyAttempt.status, 401);
  const wrongPhoneAttempt = await request("/api/location-consent/share", {
    method: "POST",
    token,
    phoneToken: "wrong-recipient-phone",
    body: { latitude: 23.8103, longitude: 90.4125, accuracy: 15 }
  });
  assert.equal(wrongPhoneAttempt.status, 403);
  assert.equal(firestore.documents.has(`locationShares/${created.requestId}`), false);
  const duplicate = await request("/api/location/requests", {
    method: "POST",
    token: "requester",
    body: { phoneNumber: "+88018-12345678" }
  });
  assert.equal(duplicate.status, 409);

  const consent = await request("/api/location-consent/", { token });
  assert.equal(consent.status, 200);
  const consentDetails = await consent.json();
  assert.equal(consentDetails.requesterName, "Requesting User");
  const publicConfig = await request("/api/location-consent/config");
  assert.deepEqual(await publicConfig.json(), {
    apiKey: "public-test-key",
    authDomain: "test.firebaseapp.com",
    projectId: "test-project",
    appId: "test-app"
  });
  const share = await request("/api/location-consent/share", {
    method: "POST",
    token,
    phoneToken: "recipient-phone",
    body: { latitude: 23.8103, longitude: 90.4125, accuracy: 15 }
  });
  assert.equal(share.status, 200);
  assert.equal((await requestRef.get()).data().status, "APPROVED");

  const strangerRead = await request(
    `/api/location/requests/${created.requestId}/location`,
    { token: "stranger" }
  );
  assert.equal(strangerRead.status, 404);
  const strangerList = await request("/api/location/requests", { token: "stranger" });
  assert.deepEqual((await strangerList.json()).requests, []);
  const authorizedRead = await request(
    `/api/location/requests/${created.requestId}/location`,
    { token: "requester" }
  );
  assert.equal(authorizedRead.status, 200);
  assert.equal((await authorizedRead.json()).location.latitude, 23.8103);
  const requesterStop = await request("/api/location-consent/stop", {
    method: "POST",
    token,
    phoneToken: "requester"
  });
  assert.equal(requesterStop.status, 401);
  assert.equal((await requestRef.get()).data().status, "APPROVED");

  const stopped = await request("/api/location-consent/stop", {
    method: "POST",
    token,
    phoneToken: "recipient-phone"
  });
  assert.equal(stopped.status, 200);
  assert.equal(firestore.documents.has(`locationShares/${created.requestId}`), false);
  const afterStop = await request(
    `/api/location/requests/${created.requestId}/location`,
    { token: "requester" }
  );
  assert.equal(afterStop.status, 403);

  const expiredResponse = await request("/api/location/requests", {
    method: "POST",
    token: "requester",
    body: { phoneNumber: "01912345678" }
  });
  const expiredRequest = await expiredResponse.json();
  const expiredRef = firestore.collection("locationRequests").doc(expiredRequest.requestId);
  await firestore.runTransaction((transaction) => {
    transaction.update(expiredRef, { expiresAt: Timestamp.fromMillis(Date.now() - 1) });
  });
  const expiredConsent = await request("/api/location-consent/", {
    token: new URL(expiredRequest.shareUrl).hash.slice("#token=".length)
  });
  assert.equal(expiredConsent.status, 410);
  assert.equal((await expiredRef.get()).data().status, "EXPIRED");

  const declineResponse = await request("/api/location/requests", {
    method: "POST",
    token: "requester",
    body: { phoneNumber: "01812345678" }
  });
  const declineRequest = await declineResponse.json();
  const declineToken = new URL(declineRequest.shareUrl).hash.slice("#token=".length);
  const declined = await request("/api/location-consent/decline", {
    method: "POST",
    token: declineToken,
    phoneToken: "recipient-phone"
  });
  assert.equal(declined.status, 200);
  const requesterHistory = await request("/api/location/requests", { token: "requester" });
  const history = await requesterHistory.json();
  assert.equal(history.requests.find((item) => item.requestId === declineRequest.requestId).status, "DECLINED");
  assert.equal(firestore.documents.has(`locationShares/${declineRequest.requestId}`), false);
});

test("browser denial does not submit coordinates and geolocation waits for the Share click", async () => {
  const token = randomBytes(32).toString("base64url");
  const elements = new Map();
  const fetchCalls = [];
  let geolocationCalls = 0;
  const element = (id) => {
    if (!elements.has(id)) {
      elements.set(id, {
        hidden: false,
        disabled: false,
        value: "",
        textContent: "",
        dataset: {},
        addEventListener(event, callback) {
          this[event] = callback;
        }
      });
    }
    return elements.get(id);
  };
  const windowObject = {
    isSecureContext: true,
    location: { hash: `#token=${token}`, pathname: "/location-share", search: "" },
    locationFirebase: {
      ready: Promise.resolve({
        async sendCode() {},
        async confirmCode() { return "verified-recipient-token"; }
      })
    },
    addEventListener() {},
    setInterval() { return 1; },
    clearInterval() {}
  };
  const sandbox = {
    window: windowObject,
    document: {
      getElementById: element,
      hidden: false,
      addEventListener() {}
    },
    navigator: {
      geolocation: {
        getCurrentPosition(_success, denied) {
          geolocationCalls += 1;
          denied({ code: 1 });
        },
        watchPosition() { throw new Error("A denied permission must not start location watch."); },
        clearWatch() {}
      }
    },
    history: { replaceState() {} },
    sessionStorage: { getItem() { return null; }, setItem() {} },
    URLSearchParams,
    Intl,
    Date,
    fetch: async (url, options) => {
      fetchCalls.push({ url, options });
      return {
        ok: true,
        status: 200,
        json: async () => ({
          requestId: "request-id",
          requesterName: "Requester",
          createdAt: Date.now(),
          expiresAt: Date.now() + 60_000,
          status: "PENDING"
        })
      };
    }
  };
  runInNewContext(await readFile(new URL("../public/location-share.js", import.meta.url), "utf8"), sandbox);
  await new Promise((resolve) => setImmediate(resolve));
  assert.equal(geolocationCalls, 0);
  element("recipient-phone").value = "01812345678";
  await element("send-code-button").click();
  element("verification-code").value = "123456";
  await element("verify-code-button").click();
  assert.equal(element("share-button").hidden, false);
  await element("share-button").click();
  assert.equal(geolocationCalls, 1);
  assert.equal(fetchCalls.length, 1);
  assert.equal(fetchCalls[0].options.method, "GET");
  assert.match(element("notice").textContent, /Location sharing was denied/);
});
