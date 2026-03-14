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
  const EMULATOR_HOST = "192.168.0.103"; // your LAN IP
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




function normalizePhone(phone) {
  phone = phone.replace(/\s+/g, "");

  if (phone.startsWith("+254")) {
    return phone.substring(1);
  }

  if (phone.startsWith("07")) {
    return "254" + phone.substring(1);
  }

  return phone;
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

  // ----- Validate input -----
  if (!merchantId || !amount || !phone) {
    throw new functions.https.HttpsError(
      "invalid-argument",
      "merchantId, amount and phone are required"
    );
  }

  const normalizedPhone = normalizePhone(phone);

  // ----- Get merchant -----
  const mDoc = await db.collection("merchants").doc(merchantId).get();

  if (!mDoc.exists) {
    throw new functions.https.HttpsError("not-found", "Merchant not found");
  }

  const merchant = mDoc.data();

  if (!merchant.payment || !merchant.payment.shortcode || !merchant.payment.passKey) {
    throw new functions.https.HttpsError(
      "failed-precondition",
      "Merchant payment configuration missing"
    );
  }

  // ----- Create payment record -----
// ----- Create payment record -----

const localTime = new Date().toLocaleString("en-KE", { timeZone: "Africa/Nairobi" });

const pRef = await db.collection("payments").add({
  merchantId,
  merchantName: merchant.displayName,
  amount,
  phone: normalizedPhone,
  status: "PENDING",
  createdAt: admin.firestore.FieldValue.serverTimestamp(), // UTC
  createdLocalTime: localTime, // ✅ store human-readable Nairobi time
});




  const paymentId = pRef.id;

  // ----- Generate STK password -----
  const shortcode = merchant.payment.shortcode;
  const passkey = merchant.payment.passKey;
  const timestamp = getTimestamp();
  const password = generatePassword(shortcode, passkey, timestamp);

  // ----- Get OAuth token -----
  const token = await getOAuthToken();

  // ----- STK Push request body -----
  const body = {
    BusinessShortCode: shortcode,
    Password: password,
    Timestamp: timestamp,
    TransactionType: "CustomerPayBillOnline",
    Amount: Math.round(amount),
    PartyA: normalizedPhone,
    PartyB: shortcode,
    PhoneNumber: normalizedPhone,
    CallBackURL: "https://us-central1-myboom-ffef4.cloudfunctions.net/stkPushCallback",
    AccountReference: paymentId,
    TransactionDesc: `Payment to ${merchant.displayName}`
  };

  try {

    const resp = await axios.post(
      `${SAFARICOM_BASE}/mpesa/stkpush/v1/processrequest`,
      body,
      {
        headers: {
          Authorization: `Bearer ${token}`
        }
      }
    );

    const mpesaResponse = resp.data;

    // ----- Save STK request info -----
    await pRef.update({
      mpesa: mpesaResponse,
      checkoutRequestId: mpesaResponse.CheckoutRequestID || null,
      merchantRequestId: mpesaResponse.MerchantRequestID || null,
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });

    return {
      success: true,
      paymentId,
      mpesaResponse
    };

  } catch (err) {

    console.error("STK Push Error:", err.response?.data || err.message);

    await pRef.update({
      status: "FAILED",
      mpesaError: err.response?.data || err.toString(),
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    });

    throw new functions.https.HttpsError(
      "internal",
      "Failed to initiate STK Push"
    );
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



exports.stkPushCallback = functions.https.onRequest(async (req, res) => {
  try {

    const callback = req.body.Body.stkCallback;

    const checkoutRequestId = callback.CheckoutRequestID;
    const resultCode = callback.ResultCode;

    // Find payment using CheckoutRequestID
    const snapshot = await db.collection("payments")
      .where("checkoutRequestId", "==", checkoutRequestId)
      .limit(1)
      .get();

    if (snapshot.empty) {
      console.error("Payment not found for:", checkoutRequestId);
      return res.status(200).send("OK");
    }

    const paymentDoc = snapshot.docs[0];
    const paymentRef = paymentDoc.ref;

    let amount = 0;

    if (callback.CallbackMetadata) {
      const items = callback.CallbackMetadata.Item;
      const amountItem = items.find(i => i.Name === "Amount");
      amount = amountItem?.Value || 0;
    }


if (resultCode === 0) {

  await paymentRef.update({
    status: "SUCCESS",
    amount,
    callback: callback,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    completedLocalTime: new Date().toLocaleString("en-KE", { timeZone: "Africa/Nairobi" }) // local Nairobi time
  });

} else {
  await paymentRef.update({
    status: "FAILED",
    callback: callback,
    updatedAt: admin.firestore.FieldValue.serverTimestamp(),
    completedLocalTime: new Date().toLocaleString("en-KE", { timeZone: "Africa/Nairobi" }) // optional
  });
}

    res.status(200).send("OK");

  } catch (error) {

    console.error("Callback error:", error);
    res.status(200).send("OK");

  }
});


