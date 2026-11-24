# Podsumowanie prac — tydzień 4

Stan na 2026-11-24. Podsumowanie obejmuje pracę Osoby 4 nad pionem partia → etap 0
(W4-10..W4-12) oraz stan pozostałych modułów widoczny w kodzie i na lokalnym stosie.
Podział ról: [team-split](../../team-split.md); plan: [week4-plan](../week4-plan.md).

## Wejście

- `main` po PR #15 (tydzień 3 Osoby 4), #13 (frontend) i #14 (indexer Adama, migracja `V7`).
- Backend ma już worker/relayer (PR #12), indexer z projekcjami i endpoint `POST /batches` z SIWE.
- Lokalny stos: Compose (PostgreSQL/Anvil/Kubo), kontrakt `0x5FbDB2315678afecb367f032d93F642f64180aa3`, stacja `ST-001` zarejestrowana.

## Osoba 4 — W4-10, W4-11, W4-12

**W4-10 (symulator: pion partia → etap 0).** Bez nowego runnera, w istniejących plikach:

- `client.py`: `create_challenge`/`create_session` (login SIWE, EIP-191 przez nowy
  `signing.sign_siwe_message`), `create_batch` (Bearer + `Idempotency-Key`),
  `get_batch_projection` (publiczny `GET /batches/{id}`); pola `batchId`/`batchNumber`
  w `CommandAccepted`.
- `scenario.py`: `nextStage` i `errorCode` w oczekiwaniach kroku; kroki negatywne
  (`httpStatus != 202`) nie przesuwają licznika etapów, więc mogą powtórzyć etap;
  `override_batch_number` (numer partii nadaje backend).
- `runner.py`: tryb czekania na `nextStage` z publicznej projekcji (z `404` przed
  potwierdzeniem partii, timeoutem i obsługą zakończonej partii) oraz kroki negatywne
  (`httpStatus` + `errorCode`). `accept-only` i `--dry-run` zostają osobnymi trybami.
- `cli.py`: `--batch-number`, `--wait-attempts`, `--wait-interval`.
- Nowe skrypty: `simulator/scripts/create-batch.sh` (operator: SIWE + `POST /batches`)
  i `simulator/scripts/run-vertical.sh` (pion jedną komendą).
- Nowe scenariusze: `ebike-vertical.yaml`, `ebike-repeat-completed-stage.yaml`.
- `pytest`: **399 passed, 1 skipped** (było 381).

**W4-11 (odbiór pionu).** Wyniki na realnym Anvil/Kubo/PostgreSQL:

| Sprawdzenie | Wynik |
|---|---|
| login SIWE operatora + `POST /batches` | `202`, polecenie utworzone (m.in. `58e5fa00…`) |
| indexer: `BatchCreated` → projekcja | `GET /batch-numbers/B-2026-0100` → `nextStage: 0`, `stale: false`, blok 6 |
| `completeStage` etapu 0 (podpis stacji + CID z Kubo) | `getBatch` → `nextStage = 1`; projekcja → `nextStage: 1`, blok 7 |
| CID/hash | bajty z Kubo (279 B) → Keccak == `reportHash` == `stage_projections.report_hash` |
| tryb czekania runnera (scenariusz pionu) | `etap 0: qaPassed=True status=ACCEPTED outcome=None nextStage=1` |

**W4-12 (dokumentacja).** Ten plik, sekcja „Pion partia → etap 0” w
`docs/guides/development.md` oraz wyniki i komendy w `simulator/scenarios/README.md`.

## Blokada pionu: sender relayera podpisuje zły typ transakcji (W4-04)

**Objaw:** polecenia `CREATE_BATCH` i `STAGE` kończą `FAILED` z kodem `SEND_REJECTED`;
żadna transakcja nie trafia do łańcucha, więc `BatchCreated`/`StageCompleted` nie
powstają i automatyczny pion nie dochodzi do `nextStage=1`.

**Przyczyna (potwierdzona w kodzie i w danych):**
`Eip1559TransactionSigner.sign` woła przeciążenie
`RawTransaction.createTransaction(chainId, nonce, gasLimit, maxPriorityFeePerGas,
maxFeePerGas, to, value, data, accessList, authorizationList)`. W Web3j 6.1.0 to
przeciążenie buduje transakcję **typu `0x04` (EIP-7702 SetCode)** i ma **inną
kolejność parametrów**: `(chainId, nonce, maxPriorityFeePerGas, maxFeePerGas,
gasLimit, …)`. Kod podaje `(gasLimit, maxPriorityFeePerGas, maxFeePerGas)`, więc
pola lądują w złych miejscach; oba typy to `BigInteger`, więc błąd się kompiluje.

**Dowód:** `transaction_attempts.signed_tx` z bazy dekoduje się jako typ `0x04`:
`[chainId=31337, nonce=0, 63353 (gasLimit), 30000000000 (maxPriorityFee),
31360272506 (maxFee), to, 0, data, [], [], 1, r, s]` — gaz w slocie opłaty
priorytetowej. Anvil odrzuca takie bajty. Ten sam `createBatch` wysłany `cast`
(poprawny typ `0x02`) przechodzi.

**Poprawka (Mikołaj, W4-04):** użyć przeciążenia EIP-1559 bez listy autoryzacji:
`RawTransaction.createTransaction(chainId, nonce, gasLimit, maxPriorityFeePerGas,
maxFeePerGas, to, value, data, accessList)` → `TransactionType.EIP1559` (typ `0x02`).
Projekt nie potrzebuje EIP-7702 (V2-09 opisuje własnego dostawcę EIP-1559).

**Konsekwencja dla odbioru:** pion działa i został udowodniony (przez `cast`), ale
**automatyczny** przebieg `run-vertical.sh` nie dojdzie do `nextStage=1`, dopóki
sender nie zostanie poprawiony. Nie zaliczam W4-11 jako pełnego pionu przez workera.

## Drugie ustalenie: harness nie nadaje OPERATOR_ROLE

`backend/scripts/local-e2e/deploy-contracts.sh` woła `contracts/script/deploy-local.sh`,
który **nie nadaje `OPERATOR_ROLE`** (robi to tylko `infrastructure/local/deploy-contract.sh`
w usłudze Compose `deploy`). Na stosie postawionym harnessem `POST /batches` zwraca
`403 ROLE_MISSING`. Rolę nadano ręcznie (`cast send grantRole …` dla Anvil #3); do
domknięcia przez Adama/Mikołaja.

## Punkty otwarte

1. **Poprawka sendera (W4-04)** — najważniejsza; bez niej nie ma automatycznego pionu.
2. `OPERATOR_ROLE` w harnessie (Adam/Mikołaj).
3. Krok negatywny `409 STAGE_ALREADY_COMPLETED` jest pokryty testami jednostkowymi;
   na żywo wymaga polecenia `CONFIRMED`, czyli działającego sendera.
4. `GET /commands/{id}` wymaga tokenu; symulator potrafi się zalogować (SIWE), ale
   runner w pionie korzysta z publicznej projekcji, żeby nie mieszać ról.
5. `WAITING_FOR_QA` (etap 2) i reorg wymagają działającego sendera — tydzień 5.

## Przekazanie do tygodnia 5

- Symulator: pion gotowy po stronie stacji (SIWE, `--batch-number`, czekanie na
  `nextStage`, kroki negatywne); `run-vertical.sh` jako docelowa komenda pionu.
- **Priorytet: poprawka sendera (W4-04)** — bez niej brak automatycznego pionu.
- Indexer i projekcje działają (Adam); harness wymaga `OPERATOR_ROLE` i włączenia workera.
- Kolejne kroki: T018/T019 (domknięcie), T021 (kandydaci/approval), T022 (panel operatora).

