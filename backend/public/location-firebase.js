const firebaseVersion = "11.10.0";
const firebaseModule = (name) =>
  import(`https://www.gstatic.com/firebasejs/${firebaseVersion}/${name}.js`);

const ready = (async () => {
    const configResponse = await fetch("/api/location-consent/config", { cache: "no-store" });
    const config = await configResponse.json().catch(() => ({}));
    if (!configResponse.ok) {
      throw new Error(config.error || "Phone verification is not configured.");
    }
    const [appSdk, authSdk] = await Promise.all([
      firebaseModule("firebase-app"),
      firebaseModule("firebase-auth")
    ]);
    const app = appSdk.initializeApp(config, "location-consent");
    const auth = authSdk.getAuth(app);
    auth.languageCode = "en";
    await authSdk.setPersistence(auth, authSdk.inMemoryPersistence);
    let verifier = null;
    let confirmation = null;

    return {
      async sendCode(phoneNumber) {
        if (verifier) verifier.clear();
        verifier = new authSdk.RecaptchaVerifier(auth, "recaptcha-container", { size: "normal" });
        confirmation = await authSdk.signInWithPhoneNumber(auth, phoneNumber, verifier);
      },
      async confirmCode(code) {
        if (!confirmation) throw new Error("Request an SMS verification code first.");
        await confirmation.confirm(code);
        if (!auth.currentUser) throw new Error("Phone verification did not sign you in.");
        return auth.currentUser.getIdToken(true);
      },
      async idToken() {
        if (!auth.currentUser) throw new Error("Verify the recipient phone number first.");
        return auth.currentUser.getIdToken();
      }
    };
  })().catch((error) => ({ initializationError: error }));

window.locationFirebase = { ready };
