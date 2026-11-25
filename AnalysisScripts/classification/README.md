# Transaction suggestion benchmark

The installed app starts a configured local model by default under Dev Server One. Owners request suggestions for individual transactions and retain the final decision. On the corrected eight-case run the model gave five category recommendations, four of them wrong. One request failed. One of its two commitment recommendations was wrong. The approved-rule baseline recommended one category correctly. This small diagnostic set cannot establish a household-wide error rate.

## Development-account measurements, 25 November 2026

Ryzen 7 5800H, 20,782,837,760 bytes of system RAM, Windows x64. The CPU build used four threads and one inference slot. The machine also has an RTX 3060 Laptop GPU. GPU memory was not measured. The server ran with offline mode, UI and logging disabled. An unauthenticated inference request returned HTTP 401. External connectivity was available to Windows during the test.

| Adapter | Task | Correct recommendations | Wrong recommendations | Abstentions (appropriate) | Invalid / failed | Accuracy including appropriate abstentions | Coverage |
|---|---|---:|---:|---:|---:|---:|---:|
| Approved rules | Category | 1 | 0 | 7 (5) | 0 / 0 | 75% | 12.5% |
| Local model | Category | 1 | 4 | 2 (2) | 0 / 1 | 37.5% | 62.5% |
| Local model | Merchant | 1 | 0 | 6 (2) | 0 / 1 | 37.5% | 12.5% |
| Local model | Commitment | 1 | 1 | 5 (5) | 0 / 1 | 75% | 25% |

The benchmark uses one synthetic author's expected answers, frozen before inference. For a category or commitment, a recommendation is correct only if the returned ID equals the expected ID. For a merchant, the scorer compares the expected label with the returned label case-insensitively. For example, “Wren Foods” is accepted for “POS 104 WREN FOODS”. Extra words that might make a label equally useful to a person can score as wrong. An abstention is appropriate only when the expected answer is null. No household owner has ratified these synthetic labels.

The rules have no merchant or commitment output. Category confirmation removes categories from the model choices and enables eligible commitment candidates. A legitimate no-match counts toward accuracy. Coverage counts only actual recommendations. The frozen held-out labels include three correct no-merchant outcomes. The tuning example is excluded from scoring.

The table uses the recheck after aligning duplicate-key rejection and string normalisation with the app. Startup took 3,827 ms, first response 5,373 ms, warm median 6,173 ms and nearest-rank p95 30,004 ms including one failed request. Windows reported a peak working set of 1,842,274,304 bytes. The initial run with the earlier scorer returned six category recommendations (one correct), seven merchant abstentions and the same two commitment recommendations. Its warm median was 2,025 ms. Both runs used the same frozen corpus. One separate live request through the packaged app took 5,837 ms; a simultaneous authenticated ledger read completed in 16 ms, and the source transaction was unchanged. The Python rule comparison took 0.03 ms on its first case and at most 0.01 ms at warm p95. The production review-queue integration test checks the same corpus against the actual approved-rule service. Development measurements do not establish installed-instance readiness.

OpenAI benchmark pending protected credentials. No billable requests or purchases were made during this run.

## Reproduce on the development account

Use PowerShell 7 and Python 3. Download the Windows CPU x64 ZIP from [llama.cpp b12728](https://github.com/ggml-org/llama.cpp/releases/tag/b12728). Verify SHA-256 `a8ace09d44bb10e2617268c55c3f2db0e0adeedd9c1ba722969920665a3ae698` before extracting. Add `qwen2.5-1.5b-instruct-q4_k_m.gguf` from [Qwen2.5-1.5B-Instruct-GGUF](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF) to the same directory. Verify SHA-256 `6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e`. Leave the original filename intact. Keep both downloads outside the checkout.

```powershell
python AnalysisScripts/classification/benchmark.py baseline
./AnalysisScripts/classification/Run-Local.ps1 -RuntimeDirectory C:\Models\gbd37
```

The launcher creates a temporary inference credential, checks authentication, measures startup and memory, and stops its server on exit. Use GBD37_PYTHON to select a particular Python executable. The benchmark reads only the versioned fixture file. Its canonical JSON hash is e59a6ca008a329133565c670506c94c6924eeca41944d849c7af9464c7541239. The prompt and four-field output contract are version 1. Each adapter receives the same synthetic description, amount, date and permitted choices. The baseline uses previously approved exact-description rules. Local inference uses JSON mode, temperature zero and a 220-token output limit.

## One authorised paid run

Supply OPENAI_API_KEY to the benchmark process through the owner's protected configuration. Confirm the account is funded and the standard model rates are still US$0.10/M input and US$0.50/M output tokens. The command uses gpt-6-luna Chat Completions, JSON mode, standard service tier and a 512-token completion cap including reasoning. It exclusively creates a run journal under `%LOCALAPPDATA%\HouseholdFinanceBenchmark`, commits each maximum cost reservation before dispatch, and retains reservations if usage is unknown. The lower of the frozen case count, 300 requests or US$1 limits this sequential run. Restarting cannot repeat it. Report any incomplete coverage, known usage and reserved allowance separately. [Model and pricing](https://developers.openai.com/api/docs/models/gpt-6-luna) · [Completion token limit](https://platform.openai.com/docs/api-reference/chat/create#chat-create-max_completion_tokens)

```powershell
python AnalysisScripts/classification/benchmark.py openai
```

## Installed instance and rollback

Under the standard Dev Server One identity, install the release containing this story using the existing Windows installation workflow. From that account, create `%LOCALAPPDATA%\HouseholdFinance\inference` under the installed private instance directory. Copy the verified extracted runtime and model there. Confirm the directory and files inherit the instance's restricted ACL. Start the installed release with `.\Start.ps1`. The launcher binds the authenticated model to 127.0.0.1:18737 and supplies an ephemeral credential to the backend. No API key is needed for app use. The model accepts one request at a time. The backend supplies at most ten eligible occurrences within seven days either side of the entry. Saving requires an explicit owner action.

Use a synthetic installed workspace for acceptance. Disconnect the network after setup, request suggestions, inspect the actor and source in audit history, exercise manual review while inference is running, and record the identity, endpoint protection and observed response times. This check is outstanding. To disable inference, stop the foreground app and restart with `.\Start.ps1 -DisableLocalSuggestions`. An absent model or a startup failure leaves manual review available. Accepted labels remain available in the ledger and encrypted backup.
