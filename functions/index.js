// functions/index.js
const functions = require("firebase-functions");
const admin = require("firebase-admin");
const axios = require("axios");
const crypto = require("crypto");

// ----- Initialize Firebase Admin -----
// ----- Initialize Firebase Admin -----
admin.initializeApp({
  credential: admin.credential.applicationDefault(),
  databaseURL: "https://myboom-ffef4.firebaseio.com", // ✅ Explicitly set your Realtime DB URL
});


// ----- Emulator detection (local dev) -----


if (process.env.FUNCTIONS_EMULATOR) {
  const EMULATOR_HOST = "192.168.1.100"; // your LAN IP
  console.log("⚡ Running in emulator mode. Connecting Firestore, Database, Storage to local emulators.");

  // Firestore
  admin.firestore().settings({ host: `${EMULATOR_HOST}:8080`, ssl: false });

  // Realtime Database
  admin.database().useEmulator(EMULATOR_HOST, 9000);

  // Storage
  //admin.storage().useEmulator(EMULATOR_HOST, 9199);
}



const db = admin.firestore();

// ----- Safaricom config -----
const safConfig = functions.config().safaricom || {};
const SAFARICOM_BASE = safConfig.base || "https://sandbox.safaricom.co.ke";
const CONSUMER_KEY = safConfig.consumer_key || "";
const CONSUMER_SECRET = safConfig.consumer_secret || "";

// ===== Helpers =====
function getTimestamp() {
  const d = new Date();
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}${pad(d.getMonth() + 1)}${pad(d.getDate())}${pad(d.getHours())}${pad(d.getMinutes())}${pad(d.getSeconds())}`;
}

function generatePassword(shortcode, passkey, timestamp) {
  return Buffer.from(shortcode + passkey + timestamp).toString("base64");
}

async function getOAuthToken() {
  const url = `${SAFARICOM_BASE}/oauth/v1/generate?grant_type=client_credentials`;
  const resp = await axios.get(url, {
    auth: { username: CONSUMER_KEY, password: CONSUMER_SECRET },
  });
  return resp.data.access_token;
}

// ===== verifyMerchant (callable) =====
exports.verifyMerchant = functions.https.onCall(async (data, context) => {
  const { type, value, token } = data || {};
  if (!type || !value) return { verified: false };

  const q = await db.collection("merchants")
    .where(`identifiers.${type}`, "array-contains", value)
    .limit(1)
    .get();

  if (q.empty) return { verified: false };

  const doc = q.docs[0];
  const m = doc.data();

  if (m.requiresToken) {
    if (!token) return { verified: false, reason: "Token required" };
    const parts = token.split(":");
    if (parts.length < 3) return { verified: false };

    const merchantCode = parts[0];
    const ts = parseInt(parts[1], 10);
    const hmac = parts.slice(2).join(":");

    const now = Math.floor(Date.now() / 1000);
    if (Math.abs(now - ts) > 120) return { verified: false, reason: "Token expired" };

    const expected = crypto.createHmac("sha256", m.secret)
      .update(`${merchantCode}|${ts}`)
      .digest("hex");

    if (expected !== hmac) return { verified: false, reason: "Invalid token" };
  }

  return {
    verified: true,
    merchantId: doc.id,
    displayName: m.displayName,
    payment: m.payment || null,
    logoUrl: m.logoUrl || null,
  };
});

// ===== createMpesaPayment (callable) =====
exports.createMpesaPayment = functions.https.onCall(async (data, context) => {
  const { merchantId, amount, phone } = data || {};
  if (!merchantId || !amount || !phone) throw new functions.https.HttpsError("invalid-argument", "Missing params");

  const mDoc = await db.collection("merchants").doc(merchantId).get();
  if (!mDoc.exists) throw new functions.https.HttpsError("not-found", "Merchant not found");

  const merchant = mDoc.data();

  const pRef = await db.collection("payments").add({
    merchantId,
    amount,
    phone,
    status: "PENDING",
    createdAt: admin.firestore.FieldValue.serverTimestamp(),
  });
  const paymentId = pRef.id;

  const shortcode = merchant.payment.shortcode;
  const passkey = merchant.payment.passKey;
  const timestamp = getTimestamp();
  const password = generatePassword(shortcode, passkey, timestamp);

  const token = await getOAuthToken();

  const body = {
    BusinessShortCode: shortcode,
    Password: password,
    Timestamp: timestamp,
    TransactionType: "CustomerPayBillOnline",
    Amount: Math.round(amount),
    PartyA: phone,
    PartyB: shortcode,
    PhoneNumber: phone,
    CallBackURL: merchant.payment.callbackUrl || `${functions.config().base.callback_url}/mpesaCallback`,
    AccountReference: merchant.displayName,
    TransactionDesc: `Payment to ${merchant.displayName} (paymentId=${paymentId})`,
  };

  try {
    const resp = await axios.post(`${SAFARICOM_BASE}/mpesa/stkpush/v1/processrequest`, body, {
      headers: { Authorization: `Bearer ${token}` },
    });

    await pRef.update({ mpesa: resp.data, updatedAt: admin.firestore.FieldValue.serverTimestamp() });

    return { success: true, paymentId, mpesaResponse: resp.data };
  } catch (err) {
    await pRef.update({
      status: "FAILED",
      mpesaError: err.toString(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });
    throw new functions.https.HttpsError("internal", "Failed to initiate STK Push");
  }
});

// ===== mpesaCallback (HTTP endpoint) =====
exports.mpesaCallback = functions.https.onRequest(async (req, res) => {
  try {
    const callback = req.body;
    const stkCallback = callback.Body && callback.Body.stkCallback;
    if (!stkCallback) {
      console.error("Invalid callback", callback);
      return res.status(400).send("Invalid callback");
    }

    const checkoutId = stkCallback.CheckoutRequestID;
    const q = await db.collection("payments").where("mpesa.CheckoutRequestID", "==", checkoutId).limit(1).get();

    if (!q.empty) {
      const pdoc = q.docs[0];
      const result = stkCallback.ResultCode === 0 ? "SUCCESS" : "FAILED";
      await pdoc.ref.update({
        status: result,
        mpesaCallback: stkCallback,
        updatedAt: admin.firestore.FieldValue.serverTimestamp()
      });
    } else {
      console.warn("Payment not found for CheckoutRequestID", checkoutId);
    }

    res.status(200).send({ message: "callback processed" });
  } catch (e) {
    console.error("Callback error", e);
    res.status(500).send("error");
  }
});

// ===== resolveMerchant (callable) =====
exports.resolveMerchant = functions.https.onCall(async (data, context) => {
  const { deviceId, signalType } = data || {};
  if (!deviceId || !signalType) {
    throw new functions.https.HttpsError("invalid-argument", "Missing deviceId or signalType");
  }

  const q = await db.collection("merchants")
    .where("ephemeralIds", "array-contains", deviceId)
    .limit(1)
    .get();

  if (q.empty) return { resolved: false };

  const doc = q.docs[0];
  const merchant = doc.data();

  return {
    resolved: true,
    merchantId: doc.id,
    displayName: merchant.displayName,
    payment: merchant.payment || null,
    logoUrl: merchant.logoUrl || null,
    detectedVia: signalType
  };
});
