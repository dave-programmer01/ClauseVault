# ClauseVault — private contract risk auditor (NEAR Private AI Hackathon)
Stack: Spring Boot 4.1 / Java 21, React (Vite), PDFBox, web3j crypto.
Model: Qwen/Qwen3-VL-30B-A3B-Instruct on NEAR AI Cloud (Confidential TEE).
GLM 5.3 Flash was tested and rejected (ignores the thinking flag, times out).

## Done
- NearAiClient: chat (raw request/response bytes kept), signature (retry), attestedAddresses (all replicas, cached, refresh).
- NearSignatureVerifier: 5 checks (model, request hash, response hash, signer in attested set, provider_tee).
- NearVerificationService: re-fetches attestation once if signer not found.
- NearAiLiveIT passes: verified=true.
- Ticket 1 (audit/ package): POST /api/audit (PDF or .txt, multipart "file") -> PdfTextService -> AuditService
  (stage 1 clauses, stage 2 risks over stage 1's exact output string, so receipts chain) -> AuditResult with
  one StageReceipt per stage; GET /api/audit/{id}. Runs kept in ConcurrentHashMap<String, AuditRun> with raw bytes.
  NearAiClient.chat(system, user): temperature 0, max_tokens 6000, stream false, response_format json_object
  (accepted by the gateway; auto-falls back on a 400). 120s read timeout. Limits: 12,000 chars (413), no text (422).
  Risks: category normalized to the eval.json labels (uppercase), unknown -> OTHER; r1.. ids and PENDING set server-side.
  samples/*.pdf generated from the .txt samples. AuditPipelineLiveIT: all 3 planted risks, verified=[true,true], ~9s.

## Rules
- Never let Jackson/Spring AI re-serialize the request: hash exactly the bytes sent.
- No LangChain/Spring AI. API key only from env var NEARAI_API_KEY; never committed.

## Todo (in order)
1. DONE (see above).
2. Human review: risks PENDING; POST /api/audit/{id}/review accept/dismiss/edit; ConcurrentHashMap store.
3. React UI: upload, risk cards (severity colors) with Accept/Dismiss, per-stage "Verified in TEE" badge + proof panel + cloud.near.ai/verify link.
4. Tamper button (flip 1 byte -> badge red); accuracy vs samples/eval.json.
5. README with one-command run; X post tags @buidlcity @NEARProtocol @nearlegion #NEARHackathon; submit by Sat Nov 14 6:59 PM ET.
