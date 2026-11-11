# ClauseVault — private contract risk auditor (NEAR Private AI Hackathon)
Stack: Spring Boot 4.1 / Java 21, React (Vite), PDFBox, web3j crypto.
Model: Qwen/Qwen3-VL-30B-A3B-Instruct on NEAR AI Cloud (Confidential TEE).
GLM 5.3 Flash was tested and rejected (ignores the thinking flag, times out).

## Done
- NearAiClient: chat (raw request/response bytes kept), signature (retry), attestedAddresses (all replicas, cached, refresh).
- NearSignatureVerifier: 5 checks (model, request hash, response hash, signer in attested set, provider_tee).
- NearVerificationService: re-fetches attestation once if signer not found.
- NearAiLiveIT passes: verified=true.
- Packages: model (records), exception (AuditException 4xx, StageException 502), service (AuditService, PdfTextService,
  StageRunner, Prompts), web (AuditController, AuditExceptionHandler), near (client + verification), config.
- Ticket 1: POST /api/audit (PDF or .txt, multipart "file") -> PdfTextService -> AuditService
  (stage 1 clauses, stage 2 risks over stage 1's exact output string, so receipts chain) -> AuditResult with
  one StageReceipt per stage; GET /api/audit/{id}. Runs kept in ConcurrentHashMap<String, AuditRun> with raw bytes.
  NearAiClient.chat(system, user): temperature 0, max_tokens 6000, stream false, response_format json_object
  (accepted by the gateway; auto-falls back on a 400). 120s read timeout. Limits: 12,000 chars (413), no text (422).
  Risks: category normalized to the eval.json labels (uppercase), unknown -> OTHER; r1.. ids and PENDING set server-side.
  samples/*.pdf generated from the .txt samples. AuditPipelineLiveIT: all 3 planted risks, verified=[true,true], ~9s.
  Live ITs (*IT.java) only run with -Dtest=..., surefire skips them by default.
- Ticket 2: POST /api/audit/{id}/review {riskId, action ACCEPT|DISMISS|EDIT, severity?, explanation?} -> full AuditResult.
  Risk has edited flag; EDIT touches only severity/explanation, sets ACCEPTED + edited=true. Receipts/raw bytes never change.
  Any state -> any state. Update runs inside ConcurrentHashMap.computeIfPresent.
- Verify link: StageReceipt.verifyUrl = https://cloud.near.ai/verify?model=<id>#receipt=<base64url JSON>
  (fields completionId, model, signingAlgo=ecdsa, signingAddress, signedText, signature, signatureKind). Confirmed by
  reading the page's JS: standalone mode reads ?model and #receipt, verifies the signature in the browser, fragment
  never reaches NEAR's server, then verifies the TDX quote + GPU evidence for the signing key via its own proxy.
  Embedded mode (iframe + postMessage) also exists; not used.
- Badge wording (decided): badge says "Verified in TEE"; proof panel says "Signed by an attested TEE key" with copy
  buttons for signer, signed text, signature + the verify link. Our 5 checks prove the signer is in NEAR's attestation
  report; hardware quote verification is delegated to NEAR's verifier (say so in README).
  The #receipt format is undocumented and can change: keep the copy buttons as the fallback, and re-open a fresh
  verifyUrl in a browser on Sat Nov 14 before submitting (confirm signer, TDX and GPU go green).
- Verify link UI rules (ticket 3/4): open with target="_blank" rel="noopener noreferrer" (otherwise the audit URL with
  the id leaks to NEAR in the Referer). Permanent caption under the link: "Checks the signature and hardware. Content
  hashes are checked by ClauseVault." After the tamper button, NEAR's page stays green (signature + signed text are
  still valid) while our badge goes red, so on a failed receipt list which of the five checks failed next to it.
- Privacy: no contract text, prompt or response body in logs or exception messages (hashes, chat ids, sizes only).
  GET /api/audit/{id} returns hashes + signature + signer, never raw bytes (those stay in AuditRun for the tamper button).

## Rules
- Never let Jackson/Spring AI re-serialize the request: hash exactly the bytes sent.
- No LangChain/Spring AI. API key only from env var NEARAI_API_KEY; never committed.

## Todo (in order)
1. DONE (see above).
2. DONE (see above).
3. IN PROGRESS. frontend/ = Vite + React 19 + TS (npm run dev on :5173, proxies /api to :8080; npm run build type-checks,
   tsconfig has erasableSyntaxOnly so no TS parameter properties). src/types.ts mirrors model records, src/api.ts
   wraps audit/getAudit/review with ProblemDetail errors. Checkpoint done: upload form renders raw JSON.
   Remaining: risk cards, accept/dismiss/edit, badges + proof panel, then serve the build from Spring (one-command run).
   React UI: upload, risk cards (severity colors) with Accept/Dismiss, per-stage "Verified in TEE" badge + proof panel + cloud.near.ai/verify link.
   Show edited=true risks as "human-edited" (badge must not vouch for edited text); hide clause type OTHER.
4. Tamper button (flip 1 byte -> badge red); accuracy vs samples/eval.json (labels are uppercase, match Risk.category).
   README: call it "3 hand-written contracts with planted risks" (stage 2 prompt was tuned on them), not general accuracy;
   add one untuned contract if time allows.
5. README with one-command run; X post tags @buidlcity @NEARProtocol @nearlegion #NEARHackathon; submit by Sat Nov 14 6:59 PM ET.
