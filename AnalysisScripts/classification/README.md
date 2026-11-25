# Transaction suggestion benchmark

Keep local suggestions disabled by default on this laptop. On the eight frozen synthetic cases, the model gave six category recommendations, five of them wrong. One of its two commitment recommendations was wrong. The approved-rule baseline recommended one category correctly. This small diagnostic set cannot establish a household-wide error rate.

## Development-account measurements, 25 November 2026

Ryzen 7 5800H, 20,782,837,760 bytes of system RAM, Windows x64. The CPU build used four threads and one inference slot. The machine also has an RTX 3060 Laptop GPU. GPU memory was not measured. The server ran with offline mode, UI and logging disabled. An unauthenticated inference request returned HTTP 401. External connectivity was available to Windows during the test.

| Adapter | Task | Correct recommendations | Wrong recommendations | Abstentions (appropriate) | Invalid / failed | Accuracy including appropriate abstentions | Coverage |
|---|---|---:|---:|---:|---:|---:|---:|
| Approved rules | Category | 1 | 0 | 7 (5) | 0 / 0 | 75% | 12.5% |
| Local model | Category | 1 | 5 | 2 (2) | 0 / 0 | 37.5% | 75% |
| Local model | Merchant | 1 | 0 | 7 (3) | 0 / 0 | 50% | 12.5% |
| Local model | Commitment | 1 | 1 | 6 (6) | 0 / 0 | 87.5% | 25% |

The rules have no merchant or commitment output. Category confirmation removes categories from the model choices and enables eligible commitment candidates. A legitimate no-match counts toward accuracy. Coverage counts only actual recommendations. The frozen held-out labels include three correct no-merchant outcomes. The tuning example is excluded from scoring.

Model loading to readiness took 2,270 ms. The first request took 2,999 ms. Across seven subsequent requests the median was 2,025 ms and the nearest-rank p95 was 2,827 ms. Windows reported a peak server working set of 1,877,520,384 bytes. The Python rule comparison took 0.03 ms on its first case and at most 0.01 ms at warm p95. The production review-queue integration test checks the same corpus against the actual approved-rule service. Development measurements do not establish installed-instance readiness.

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

Under the standard Dev Server One identity, install the release containing this story using the existing Windows installation workflow. From that account, create `%LOCALAPPDATA%\HouseholdFinance\inference` under the installed private instance directory. Copy the verified extracted runtime and model there. Confirm the directory and files inherit the instance's restricted ACL. Start the installed release with `.\Start.ps1 -LocalSuggestions`. The launcher binds the authenticated model to 127.0.0.1:18737 and supplies an ephemeral credential to the backend. No API key is needed for app use. The model accepts one request at a time. The backend supplies at most ten eligible occurrences within seven days either side of the entry. Saving requires an explicit owner action.

Use a synthetic installed workspace for acceptance. Disconnect the network after setup, request suggestions, inspect the actor and source in audit history, exercise manual review while inference is running, and record the identity, endpoint protection and observed response times. This check is outstanding. To disable inference, stop the foreground app and restart with `.\Start.ps1`. Accepted labels remain available in the ledger and encrypted backup.
