const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_scholar_123";
const BOB_UID = "bob_scholar_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp(offsetMs = 5000) {
  return new Date(Date.now() - offsetMs);
}

function futureTimestamp(offsetMs = 60000) {
  return new Date(Date.now() + offsetMs);
}

function validNotePayload(uid, overrides = {}) {
  const ts = pastTimestamp();
  return {
    userId: uid,
    title: "Laws of Motion Summary",
    subject: "Physics",
    category: "SUMMARY",
    style: "Short Notes",
    sourceName: "chapter3.pdf",
    content: "Newton's First Law: An object stays at rest or in uniform motion unless acted on by a net external force.",
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function validQuizPayload(uid, overrides = {}) {
  const ts = pastTimestamp();
  return {
    userId: uid,
    title: "Physics Mock Test",
    subject: "Physics",
    sourceType: "MOCK_TEST",
    sourceName: "Mechanics",
    difficulty: "Medium",
    questionType: "MCQ",
    durationMinutes: 15,
    questionCount: 1,
    questions: [
      {
        index: 0,
        question: "What is the SI unit of force?",
        options: ["Joule", "Newton", "Watt", "Pascal"],
        correctAnswer: "Newton",
        explanation: "Force is measured in Newtons.",
        type: "MCQ",
      },
    ],
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

function validAttemptPayload(uid, quizId = "quiz_1", overrides = {}) {
  const ts = pastTimestamp();
  return {
    userId: uid,
    quizId,
    quizTitle: "Physics Mock Test",
    subject: "Physics",
    mockTest: true,
    score: 1,
    totalQuestions: 1,
    percentage: 100,
    timeSpentSeconds: 95,
    userAnswers: { "0": "Newton" },
    completedAt: ts,
    createdAt: ts,
    updatedAt: ts,
    ...overrides,
  };
}

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

// --- 1. UNAUTHENTICATED ACCESS DENIAL ---

test("Unauthenticated users cannot read or write profiles, notes, quizzes, or quizAttempts", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();

  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("notes").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("notes")
      .doc("note_1")
      .set(validNotePayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("quizzes").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("quizzes")
      .doc("quiz_1")
      .set(validQuizPayload(ALICE_UID))
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("quizAttempts").get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("quizAttempts")
      .doc("attempt_1")
      .set(validAttemptPayload(ALICE_UID))
  );
});

// --- 2. USER PROFILE SECURITY & PII ISOLATION ---

test("Owner can create and update own profile; cannot list users or read another user's profile", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastTimestamp();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Scholar",
      email: "alice@example.com",
      preferredLanguage: "ENGLISH",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).get());
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice S.",
      preferredLanguage: "TAMIL",
      updatedAt: pastTimestamp(1000),
    })
  );

  // Listing all users is forbidden
  await assertFails(aliceDb.collection("users").get());

  // Cross-user PII read/write is forbidden
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: pastTimestamp(500),
    })
  );

  // Shadow field injection is rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastTimestamp(500),
    })
  );
});

// --- 3. STUDY NOTES CRUD, VALIDATION & ISOLATION ---

test("Owner can create, list, rename, and delete notes; invalid or ghost fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const notesRef = aliceDb.collection("users").doc(ALICE_UID).collection("notes");

  // Empty subcollection list query (exact Kotlin repository query shape)
  await assertSucceeds(notesRef.orderBy("createdAt", "desc").get());

  // Create valid note (NO @DocumentId `id` field)
  await assertSucceeds(notesRef.doc("note_1").set(validNotePayload(ALICE_UID)));

  // List non-empty subcollection
  await assertSucceeds(notesRef.orderBy("createdAt", "desc").get());

  // Partial rename update (exact Kotlin renameNote payload)
  await assertSucceeds(
    notesRef.doc("note_1").update({
      title: "Newton's Laws Revised",
      updatedAt: pastTimestamp(1000),
    })
  );

  // Reject mutating immutable createdAt
  await assertFails(
    notesRef.doc("note_1").update({
      createdAt: pastTimestamp(500),
      updatedAt: pastTimestamp(500),
    })
  );

  // Reject @DocumentId `id` field in create payload
  await assertFails(
    notesRef
      .doc("note_2")
      .set(validNotePayload(ALICE_UID, { id: "note_2" }))
  );

  // Reject future tampered timestamp
  await assertFails(
    notesRef
      .doc("note_3")
      .set(validNotePayload(ALICE_UID, { createdAt: futureTimestamp() }))
  );

  // Reject invalid category enum
  await assertFails(
    notesRef
      .doc("note_4")
      .set(validNotePayload(ALICE_UID, { category: "UNKNOWN" }))
  );

  // Reject cross-user access
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("notes").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("notes")
      .doc("note_1")
      .delete()
  );

  // Owner can delete
  await assertSucceeds(notesRef.doc("note_1").delete());
});

// --- 4. QUIZZES CRUD, VALIDATION & ISOLATION ---

test("Owner can create, list, rename, and delete quizzes; invalid shapes fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const quizzesRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("quizzes");

  // Exact Kotlin query shape
  await assertSucceeds(quizzesRef.orderBy("createdAt", "desc").get());

  // Create valid quiz
  await assertSucceeds(
    quizzesRef.doc("quiz_1").set(validQuizPayload(ALICE_UID))
  );

  // Rename quiz (exact Kotlin renameQuiz payload)
  await assertSucceeds(
    quizzesRef.doc("quiz_1").update({
      title: "Mechanics Mastery Quiz",
      updatedAt: pastTimestamp(1000),
    })
  );

  // Reject unauthorized field update on quiz (only title & updatedAt allowed)
  await assertFails(
    quizzesRef.doc("quiz_1").update({
      difficulty: "Easy",
      updatedAt: pastTimestamp(1000),
    })
  );

  // Reject empty questions list
  await assertFails(
    quizzesRef
      .doc("quiz_2")
      .set(validQuizPayload(ALICE_UID, { questions: [] }))
  );

  // Reject cross-user read/write
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("quizzes")
      .doc("quiz_1")
      .get()
  );

  // Owner can delete
  await assertSucceeds(quizzesRef.doc("quiz_1").delete());
});

// --- 5. QUIZ ATTEMPTS (HISTORY) CRUD, IMMUTABILITY & ISOLATION ---

test("Owner can create, list, and delete quiz attempts; updates and spoofed scores fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const attemptsRef = aliceDb
    .collection("users")
    .doc(ALICE_UID)
    .collection("quizAttempts");

  // Exact Kotlin query shape
  await assertSucceeds(attemptsRef.orderBy("completedAt", "desc").get());

  // Create valid attempt
  await assertSucceeds(
    attemptsRef.doc("attempt_1").set(validAttemptPayload(ALICE_UID, "quiz_1"))
  );

  // Quiz attempts are immutable historical records (updates denied)
  await assertFails(
    attemptsRef.doc("attempt_1").update({
      score: 100,
      percentage: 100,
    })
  );

  // Reject score > totalQuestions
  await assertFails(
    attemptsRef
      .doc("attempt_2")
      .set(validAttemptPayload(ALICE_UID, "quiz_1", { score: 5, totalQuestions: 1 }))
  );

  // Reject spoofed userId
  await assertFails(
    attemptsRef
      .doc("attempt_3")
      .set(validAttemptPayload(BOB_UID, "quiz_1"))
  );

  // Reject cross-user read/delete
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("quizAttempts").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("quizAttempts")
      .doc("attempt_1")
      .delete()
  );

  // Owner can delete
  await assertSucceeds(attemptsRef.doc("attempt_1").delete());
});
