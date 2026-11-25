const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ADMIN_UID = "admin_sleepycoder";
const ADMIN_EMAIL = "sleepycoder12@gmail.com";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validMasjidPayload(ownerId, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    ownerId,
    masjidName: "Masjid Al-Noor",
    cityLabel: "San Jose, CA",
    shareCode: "ALNOOR",
    publicBoard: true,
    useAutoLocationForEmpty: true,
    fajrAzan: "05:30",
    dhuhrAzan: "12:15",
    asrAzan: "15:30",
    maghribAzan: "",
    ishaAzan: "19:15",
    fajrJamaatOffsetMin: 15,
    dhuhrJamaatOffsetMin: 15,
    asrJamaatOffsetMin: 15,
    maghribJamaatOffsetMin: 5,
    ishaJamaatOffsetMin: 15,
    fajrJamaatOverride: "",
    dhuhrJamaatOverride: "",
    asrJamaatOverride: "",
    maghribJamaatOverride: "",
    ishaJamaatOverride: "",
    announcement: "Jummah Khutbah at 1:15 PM",
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

function validUserPrefsPayload(userId, overrides = {}) {
  const now = new Date(Date.now() - 2000);
  return {
    userId,
    subscribedMasjidId: "community_main",
    calculationMethod: "ISNA",
    asrMethod: "STANDARD",
    latitude: 37.3382,
    longitude: -121.8863,
    locationLabel: "San Jose, CA",
    azanVoiceEnabled: true,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  };
}

test("Unauthenticated users cannot read or write masjids or users", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("masjids").doc("community_main").get());
  await assertFails(unauthDb.collection("masjids").where("publicBoard", "==", true).get());
  await assertFails(
    unauthDb.collection("masjids").doc("masjid_1").set(validMasjidPayload(ALICE_UID))
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
});

test("Signed-in user can safely get non-existent community_main without PERMISSION_DENIED", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true }).firestore();
  await assertSucceeds(aliceDb.collection("masjids").doc("community_main").get());
});

test("Only verified bootstrapped admin can create and update community_main; spoofed unverified email fails", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const spoofDb = testEnv
    .authenticatedContext("spoof_uid", { email: ADMIN_EMAIL, email_verified: false })
    .firestore();
  const aliceDb = testEnv
    .authenticatedContext(ALICE_UID, { email: "alice@example.com", email_verified: true })
    .firestore();

  // Spoofed unverified email rejected
  await assertFails(
    spoofDb.collection("masjids").doc("community_main").set(validMasjidPayload("spoof_uid"))
  );

  // Regular user rejected from creating community_main
  await assertFails(
    aliceDb.collection("masjids").doc("community_main").set(validMasjidPayload(ALICE_UID))
  );

  // Verified admin creates community_main
  await assertSucceeds(
    adminDb.collection("masjids").doc("community_main").set(validMasjidPayload(ADMIN_UID))
  );

  // Regular user can read community_main, cannot update it
  await assertSucceeds(aliceDb.collection("masjids").doc("community_main").get());
  await assertFails(
    aliceDb.collection("masjids").doc("community_main").update({
      fajrAzan: "05:00",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Verified admin can partially update community_main
  await assertSucceeds(
    adminDb.collection("masjids").doc("community_main").update({
      fajrAzan: "05:15",
      maghribJamaatOffsetMin: 5,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("Regular user can create & update their own Masjid board, and others can read public boards via filtered query", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("masjids").doc("alice_masjid").set(validMasjidPayload(ALICE_UID))
  );

  // Reject ghost field / @DocumentId 'id' field
  await assertFails(
    aliceDb.collection("masjids").doc("alice_masjid_2").set({
      ...validMasjidPayload(ALICE_UID),
      id: "alice_masjid_2",
    })
  );

  // Reject invalid time format or out-of-range offset
  await assertFails(
    aliceDb.collection("masjids").doc("alice_masjid").update({
      fajrAzan: "99:99",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(
    aliceDb.collection("masjids").doc("alice_masjid").update({
      maghribJamaatOffsetMin: 999,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Bob can list public boards with filter, cannot list unfiltered, cannot edit Alice's board
  await assertSucceeds(bobDb.collection("masjids").where("publicBoard", "==", true).get());
  await assertFails(bobDb.collection("masjids").get());
  await assertFails(
    bobDb.collection("masjids").doc("alice_masjid").update({
      masjidName: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Alice cannot mutate immutable createdAt or ownerId
  await assertFails(
    aliceDb.collection("masjids").doc("alice_masjid").update({
      ownerId: BOB_UID,
      updatedAt: new Date(Date.now() - 500),
    })
  );
});

test("UserPreferences strictly isolated to owner", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserPrefsPayload(ALICE_UID))
  );
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      azanVoiceEnabled: false,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Bob cannot read or write Alice's preferences
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).set(validUserPrefsPayload(BOB_UID))
  );
});
