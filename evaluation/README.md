# Research Evaluation Framework — Phase 3

Framework đo Code Review bằng dữ liệu gán nhãn và điều kiện cố định. **Không có kết quả chất lượng Gemini thật trong các mẫu bàn giao.** Không chạy Spring application, không dùng Task API/DB và không gọi provider khi load config, dry-run hoặc replay.

Framework này là công cụ dòng lệnh độc lập phục vụ nghiên cứu; ứng dụng chính chạy trong cửa sổ desktop Linux như mô tả tại [README của dự án](../README.md).

## Cấu trúc và kiến trúc

```text
evaluation/
  datasets/       development.json, test.json: version, project split, source hashes
  fixtures/       bốn project Java/Spring nhỏ, không chứa đáp án
  ground-truth/   annotations và schema-v1.json, nằm ngoài sandbox model
  configs/        cấu hình offline mặc định
  replays/        báo cáo synthetic do người viết để kiểm thử evaluator
  results/       mỗi experiment một thư mục riêng, checkpoint và exports
  reports/       so sánh các experiment có điều kiện tương đương
  protocol.md    rubric, metrics, quy trình nghiên cứu
```

Java nằm trong `src/main/java/prj/anhzxje/aiagent/evaluation/`, không có `@Component` và không được inject vào production workflow:

| Thành phần | Trách nhiệm |
| --- | --- |
| EvaluationTypes | Records versioned cho dataset, annotations, config, generation, scoring |
| EvaluationDataset | Kiểm schema, hash, evidence/line, duplicate IDs, leakage giữa hai split |
| ExperimentConfiguration | Config/budget/skill pinning, gate phê duyệt REAL_LLM |
| EvaluationGeneration | ChatClient + PromptComposer + SandboxTools + ReviewBudgetAdvisor; trả raw output trước acceptance validation |
| EvaluationValidation | Schema validator production và đối chiếu evidence với source snapshot/observed ranges |
| FindingMatcher | Exact identity, candidates, manual adjudication; một-một |
| EvaluationScorer | TP/FP/FN, micro metrics, category/family, evidence/location/severity |
| EvaluationRunner | Mode, subset, caps, generation reuse, checkpoint/resume và dừng budget/quota |
| EvaluationExporter | Summary/per-case CSV, JSON, adjudication queue, Markdown |
| ExperimentComparison | Từ chối pooling nếu khác model/options/source/budget/protocol |
| EvaluationFiles | Bounded local I/O, hashes, redaction |
| EvaluationCli | CLI thuần; `scripts/evaluate.sh` dùng Maven/JUnit launcher, không dựng Spring context |

Luồng: validate config/dataset → chọn cases → gate real nếu cần → generate/replay → chẩn đoán validation → matching → scoring → atomic checkpoint → exports. R2 dùng lại generation R1, không gọi model định dạng lại.

## Dataset 1.0.0

Dataset schema version 1; `groundTruthSchemaVersion:1` chọn contract của các annotation arrays. Mỗi annotation có sourceRevision SHA-256, category, severity, classification, nhiều location nếu cần, root cause, trigger, impact, counterevidence, verification procedure và rationale.

| Split | Project | Nhãn |
| --- | --- | --- |
| development | inventory-api | 10 confirmed defects: SQL injection, IDOR, unsafe file access, hash exposure, wrong condition, null handling, JDBC N+1, repeated regex compile, quadratic collection processing, Files.lines leak |
| development | directory-api | 4 negative controls: query tham số hóa, ownership check, DTO validation, cleanup đúng |
| test | booking-api | 4 confirmed defects: upper boundary, state transition, receipt IDOR, thiếu cleanup khi parse lỗi |
| test | ledger-api | 2 negative controls: concurrency cùng monitor, validation nhiều tầng |

Tổng: **14 confirmed defects, 6 non-bug controls**. Enum còn hỗ trợ POTENTIAL_RISK/SUBJECTIVE_IMPROVEMENT; primary recall không tính các nhãn đó như confirmed defects. Xem protocol trước khi diễn giải FP/pending.

Project có Controller/Service/Repository hoặc domain service và SecurityFilterChain khi cần context. Authentication không tự chứng minh ownership. POST verification cần một request hợp lệ về CSRF; fixture không nhằm đo CSRF. Các project là mục tiêu static review và được compile bằng classpath hiện tại trong test; không tự khởi động web server/database. Quy trình runtime dùng disposable DB/file, tuyệt đối không dùng credentials thật.

Source chỉ có `.java`, `.xml`, `.txt`; không nhận symlink/secret file. Ground truth/replay/config không nằm trong root mà SandboxTools được cấp. Kiểm tra hash toàn project, line/evidence và compile offline giúp bắt annotation drift; **không thay thế review nhãn độc lập bởi con người**. Chia theo project, vẫn có family overlap; không tuyên bố generalization sang bug family mới. Không đổi test set vì model đoán sai.

## Chạy offline

Yêu cầu Java 21, Maven đã cài và dependency đã cache. Script luôn truyền Maven `-o`; nếu cache thiếu sẽ fail, không tự tải Maven/dependency. Không tự source `.env`.

Chạy từ root repository:

```bash
bash scripts/evaluate.sh --help
bash scripts/evaluate.sh --config configs/offline-development.json --dry-run
bash scripts/evaluate.sh --config configs/offline-development.json --id my-dev-run
bash scripts/evaluate.sh --config configs/offline-development.json --id my-dev-run --resume
bash scripts/evaluate.sh --config configs/offline-test.json --id my-test-run
```

Mỗi ID chỉ tạo mới một lần. Nếu đã tồn tại, dùng `--resume` với cùng conditions hoặc chọn ID mới; không có `--overwrite`. Resume kiểm fingerprint config/dataset/truth/replay/code/prompt/skills/POM, checksum và identity checkpoint. File lock được hệ điều hành giải phóng nếu process chết. Partial `.tmp` không được coi là completed case. Trước mỗi generation có attempt marker; nếu process chết sau dispatch mà chưa lưu output, resume ghi INTERRUPTED_GENERATION_NOT_RETRIED và dừng phần còn lại vì usage chưa biết, tuyệt đối không tự gọi lại.

```bash
bash scripts/evaluate.sh --config configs/offline-development.json \
  --id my-subset --groups R1,R2 --subset inventory-api --max-cases 1
bash scripts/evaluate.sh --config configs/offline-development.json \
  --mode MOCK_INTEGRATION --id my-mock
```

MOCK_INTEGRATION tạo mock ChatModel có tool options đúng, thực hiện vòng đọc source thật qua ChatClient/SandboxTools rồi trả báo cáo rỗng cố định. Nó kiểm tra pipeline/counters/budget, **không kiểm tra năng lực tìm bug của Gemini**.

Để so hai lần chạy cùng conditions:

```bash
bash scripts/evaluate.sh --config configs/offline-development.json --id my-replay-a
bash scripts/evaluate.sh --config configs/offline-development.json --id my-replay-b
bash scripts/evaluate.sh --compare my-replay-a,my-replay-b --comparison-id my-comparison
```

Không so development với test bằng lệnh comparison như cùng source; khác snapshots phải báo thành hai strata. `--model` được hỗ trợ và ghi vào config mới; dùng cùng ID trên resume với model khác bị từ chối. Không tự đổi model sau lỗi.

## Các nhóm thí nghiệm

| Nhóm | Generation | Acceptance |
| --- | --- | --- |
| R0 | Phase-2 controlled no-skill baseline; cùng system/schema, source metadata, tools, options và caps | Giữ raw findings; validation chỉ đo chẩn đoán |
| R1 | Cùng pipeline, thêm skill ID/version đã pin | Giữ raw findings; validation chỉ đo chẩn đoán |
| R2 | **Cùng generation hash của R1** | Whole-report rejection theo schema v2/evidence; giữ raw và rejected metrics |

R0 không phải historical Phase 0 và không phải Chat `/review`; đây là giới hạn tái tạo baseline cũ. Các nhóm đều có hướng dẫn evidence trong prompt; R1/R2 đo acceptance validation, không thử một prompt không nhắc evidence. Null/empty skills trong production không thay đổi.

Invalid schema không làm mất raw issue array nếu parse được. R2 reject toàn report nếu một issue thiếu evidence, giống policy production; do đó có thể mất cả true positives khác trong report. Không tự kết luận R2 tăng recall/precision. Failures/not-run sau stop vẫn đóng góp FN theo primary intention-to-evaluate scoring, kèm status riêng.

## Output và dữ liệu mẫu

Mỗi experiment có `experiment.json`, `*.generation.json`, `*.case.json`, `experiment-results.json`, `per-case.json/csv`, `summary.csv`, `adjudication-queue.json`, `research-report.md`; `stop.json` nếu chạm budget/quota. Internal generation/case checkpoints có envelope `sha256`/`data`; JSON export dùng records phẳng.

Manifest chứa timestamp UTC, dataset hash/version, ground-truth schema version, fixture revisions, config/options/model/skills/caps, Java version, prompt schema version, framework/POM hash và matching protocol. Mỗi case ghi skillSelections ([] cho R0); effectiveModel chỉ được điền cho mock/live generation, null trong offline replay vì không có dispatch model quan sát được. Không lưu key/token/env. Các observation chỉ lưu path/range, không full tool transcript.

`storeRawFindings` quyết định lưu raw report/findings đã redaction. Nếu false, counts/matching/metrics vẫn được lưu. Nếu crash trước R2 checkpoint và raw không được lưu, resume ghi `RAW_NOT_STORED_RESUME` thay vì tự gọi lại model. Giữ raw toy-fixture output được phép giúp tái lập/adjudicate đầy đủ hơn; mặc định tài liệu real bên dưới khuyến nghị false.

Các exports có provenance SYNTHETIC_FIXTURE, MOCK_INTEGRATION hoặc ACTUAL_LLM, cùng execution mode. Replay import từ model cũ phải dùng RECORDED_LLM_UNVERIFIED, không được tự gắn ACTUAL_LLM. Bộ replays mẫu cố ý có misses, duplicates và false alarms để kiểm phép đo; khác biệt số giữa R0/R1 ở đây là do người viết fixture, **không phải kết quả model**.

Raw/source chỉ giới hạn ở toy fixtures được hash. Directory experiment dùng owner-only permissions khi POSIX hỗ trợ. Redaction là defense in depth, không phải bộ phát hiện mọi secret; không thêm repo/source nhạy cảm vào bộ dữ liệu này. CSV escape dấu phẩy/quote/newline và chặn formula injection.

## REAL_LLM — chỉ sau phê duyệt riêng

**Chưa chạy trong Phase 3 bàn giao.** Không có approval document có hiệu lực được tạo sẵn. Cờ `--real-llm` riêng lẻ không đủ.

1. Tạo bản sao config với `mode:REAL_LLM`, kiểm model/quyền sử dụng/quota của tài khoản, chốt subset/caps và `storeRawFindings`. Không dùng ChatService làm generator. Nếu không cần lưu raw, chọn false và chấp nhận giới hạn resume nêu trên.
2. Chạy `--dry-run` với đúng config để lấy configHash và phạm vi tối đa. Không gọi provider để kiểm tra model/quota.
3. Người có thẩm quyền tạo file riêng ngoài repo, quyền 0600, cấu trúc sau với hash thật và thời điểm hết hạn ngắn; không dùng các placeholder này để chạy:

```json
{
  "approved": true,
  "approvedBy": "operator-name",
  "experimentId": "approved-experiment-id",
  "configHash": "HASH_FROM_DRY_RUN",
  "expiresAt": "REPLACE_WITH_SHORT_UTC_EXPIRY"
}
```

4. Chỉ khi đã được phép, cung cấp GOOGLE_API_KEY qua cơ chế secret của môi trường rồi chạy:

```text
bash scripts/evaluate.sh --config configs/YOUR_APPROVED_CONFIG.json --id approved-experiment-id \
  --real-llm --approval-file /private/approval.json \
  --confirm APPROVE_REAL_LLM:approved-experiment-id:HASH_FROM_DRY_RUN
```

Đây là hướng dẫn vận hành, không phải sự phê duyệt chạy. Không lưu key vào config, shell command hoặc report. Gate kiểm ID/hash/người duyệt/expiry trước khi tạo provider; không tự đọc `.env`, không key-verification call. Đây là guard chống chạy nhầm trong CLI local, không phải hệ thống phân quyền ký số chống operator tự sửa code/file.

Giới hạn config mẫu: 2 fixtures × 2 physical generations (R0 + R1) × tối đa 25 dispatch = **100 model dispatches**, tối đa **96 tool invocations**; R2 không tăng các con số đó. Mỗi generation unit (fixture/group) tối đa 25 dispatch và 24 tools, prompt 24.000/context 96.000 ký tự, max output 4.096 tokens, concurrency=1. Một thử nghiệm nhỏ R1/R2 trên 1 fixture cần trần 25 dispatch/24 tools, có thể cấu hình thấp hơn trước khi duyệt. Không tăng budget để cứu kết quả xấu.

`maxRequestsPerCase/maxTotalRequests` giới hạn generation dispatches; SDK attempts=1 và Spring retry maxRetries=0, không repair/fallback/quota retry. Đây là trần lời gọi generation, **không phải telemetry số HTTP requests hoặc quota còn lại của provider**. Không tự suy ra chi phí thực tế hoặc đảm bảo billing của tài khoản chỉ từ model ID. Ngân sách thực nghiệm cưỡng chế ở đây là số calls/tools/cases/context, không phải hard USD billing cap.

Token usage chỉ cộng từ response usage của từng dispatch, EmptyUsage/missing response giữ null. Nếu một dispatch thiếu usage thì tổng token/cost không được coi là biết đầy đủ. Optional `pricing` gồm `currency:USD`, inputUsdPerMillionTokens, outputUsdPerMillionTokens, source URL và effectiveDate; người vận hành xác minh giá phù hợp model trước khi chạy. Loader không fetch URL. Không có giá cấu hình trong mẫu; estimatedCostUsd=null.

## Thêm fixture và tái lập

1. Viết project nhỏ dưới fixtures với tên source không tiết lộ bug. Đặt context callers/config/validation và test data giả cần thiết cùng project; không đưa annotation vào source/comment.
2. Giải thích trigger, impact, counterevidence, correction alternatives và verification; nhờ reviewer khác đọc source. Nhãn risk/non-bug/suggestion không tự trở thành confirmed defect.
3. Hash project: sort relative POSIX path; mỗi file thành `path:sha256(bytes)\n`; SHA-256 của chuỗi UTF-8 đó là sourceHash/sourceRevision. Hash bao gồm POM và data files.
4. Thêm annotation array theo schema-v1.json ngoài root source; location phải chứa evidence thật. Thêm vào đúng manifest, tăng dataset version khi thay dữ liệu đã phát hành. Split theo project/family; loader chặn project/Java bytes trùng, không phát hiện hết semantic near-duplicate.
5. Chạy EvaluationDatasetTest (compile từng fixture, kiểm hashes/labels), thêm synthetic replay cho R0/R1 nếu muốn demo. Không đưa expected findings vào prompt. Đăng ký config/subset/caps trước experiment thật.
6. Dùng ID mới; giữ source/config/code đã hash để resume. Thay adjudication tạo **config/experiment mới để replay**, không sửa kết quả cũ hay tự gọi lại model. Xem protocol cho scoring/adjudication.

## Verification và trạng thái

```bash
mvn -o -q -Daether.syncContext.named.factory=rwlock-local test
node --test --test-isolation=none src/test/js/*.test.cjs
node src/test/js/workspace.browser.cjs
```

Kết quả cuối, demo artifacts và mức sẵn sàng được ghi tại `evaluation/DELIVERY.md`. Không tự chuyển Phase 4. CODE-REVIEW-QUALITY-001 vẫn mở cho nghiệm thu tổng thể Phase 6; framework này cung cấp công cụ kiểm chứng, không đóng issue bằng kết quả synthetic.

## Groq pilot (Phase 3)

`configs/groq-pilot.json` chỉ chọn `inventory-api`, `groq` / `openai/gpt-oss-120b`, R0/R1 và R2 dùng lại R1. Trần là 5 dispatch mỗi generation, 10 toàn experiment, 8 tools mỗi generation, concurrency 1 và không retry. Đủ budget hay không phụ thuộc số tool call và kích thước prompt; hết budget sẽ dừng, không tăng tự động. Chỉ evaluation tạo adapter Spring AI OpenAI, với endpoint `https://api.groq.com/openai/v1`, key lấy riêng từ `GROQ_API_KEY`; production Gemini không đổi.

Guard quota cục bộ đặt trước dispatch, giữ cửa sổ 60 giây/24 giờ trong bộ nhớ của generator. Nó giữ tối đa 30/1.000 reservations và 8.000/200.000 token ước lượng tương ứng. Mỗi reservation ước lượng bằng một nửa số byte UTF-8 của messages và tool schemas cộng overhead 1.024 byte, rồi cộng max output 1.024 token; nếu chỉ vượt giới hạn phút thì chờ reservation cũ hết hiệu lực; request riêng lẻ hoặc giới hạn ngày vượt ngưỡng sẽ fail-closed, không retry. Đây là ước lượng bảo thủ, **không phải telemetry provider hay bảo đảm hard TPM/TPD**: tokenizer, overhead provider và usage thực tế có thể khác. Guard không chia sẻ giữa process, không tồn tại sau restart, không biết lưu lượng tài khoản bên ngoài và không thể bảo đảm hạn mức tài khoản; không chạy song song, kiểm tra quota thực tế trước khi được phê duyệt. Các con số 30 RPM, 1.000 RPD, 8.000 TPM, 200.000 TPD là giả định pilot được yêu cầu, không phải xác nhận quota hiện tại của tài khoản.

Dry-run (không cần key, không tạo provider hay gửi request):

```bash
bash scripts/evaluate.sh --config configs/groq-pilot.json --groups R0,R1,R2 --max-cases 1 --dry-run
```

Chỉ sau khi có phê duyệt riêng đúng ID/hash và cung cấp `GROQ_API_KEY` qua secret environment, lệnh dự kiến là:

```bash
bash scripts/evaluate.sh --config configs/groq-pilot.json --id approved-groq-pilot \
  --groups R0,R1,R2 --max-cases 1 --real-llm \
  --approval-file /private/approval.json \
  --confirm APPROVE_REAL_LLM:approved-groq-pilot:HASH_FROM_DRY_RUN
```

Không có approval có hiệu lực được tạo bởi hướng dẫn này. Dry-run không xác nhận model, quota hay khả năng tool calling của Groq thực tế.
