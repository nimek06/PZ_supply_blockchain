# Plan tygodnia 4 — pierwszy lokalny przepływ on-chain

**Data planu:** 2026-11-11. **Status:** do wykonania po
[tygodniu 3](../week3/week3-plan.md), nie raport gotowych integracji.
Podstawa: [plan projektu](../../plan.md), [podział zadań](../../team-split.md),
[spec](../../../project/spec.md) i [wariant 1 / DEC-17](../../../decisions/local-blockchain-scope.md).

## Plany osobiste

- [Adam — indexer i projekcje](plans/adam-plan.md)
- [Mikołaj — relayer i worker](plans/mikolaj-plan.md)
- [Kuba — frontend na rzeczywistych danych](plans/kuba-plan.md)
- [Robert — scenariusze i odbiór E2E](plans/robert-plan.md)

Plany osobiste rozwijają ten sam podział W4-01..W4-12, nie dodają nowych zadań.
W razie zmiany zakresu należy zaktualizować plan wspólny i właściwy plan osoby.

## 1. Cel i granice

**Minimalny pion:** operator tworzy partię → trwałe polecenie → relayer
wykonuje `createBatch` → indexer potwierdza partię → symulator zgłasza etap 0
→ Kubo przechowuje JCS → relayer wykonuje `completeStage` → event → projekcja
i rzeczywisty status w API/UI. `getBatch.nextStage` ma wynosić **1**.

To pierwszy potwierdzony etap, **nie kompletna partia 0..3**.
W tygodniu 5 rozwijamy worker, kolejne etapy i kolejkę QA; w tygodniu 6
podłączamy panel podpisu operatora. W tygodniu 4 poprawny raport kontroli
może trafić do `WAITING_FOR_QA`, ale nie może przejść bez drugiego podpisu.

Wszystko działa na Anvil/Kubo/PostgreSQL, z jednym writerem relayera.
Nie robimy Amoy, Pinaty, fee-bump, mostu, kotwiczenia, nowego runnera
ani pełnego paszportu. Korzystamy z istniejącego kodu i kontroli CI.
Zasady projektowe wynikają z [architektury](../../../architecture/architecture.md),
[protokołu](../../../architecture/protocol.md), [modelu danych](../../../architecture/data-model.md)
i [testów](../../../guides/testing.md), bez nowych wytycznych migracyjnych.

## 2. Wejście i uzgodnienia

- Deploy, adresy, role, certyfikaty i bezpieczne trwałe przyjęcie z W3-01..W3-06.
- Działający `KuboReportStorage` i signer/encoder z wcześniejszych tygodni.
- Porty przejęcia/lease, prób transakcji i checkpoint/projekcji uzgodnione
  przez Adama i Mikołaja; dotychczasowe migracje V1–V5 nie są przepisywane.
- Lokalny frontend i sesja SIWE z W3-07..W3-09; runner z W3-10.
- `latest`, 1 potwierdzenie: blok zawierający transakcję liczy się jako pierwsze.
  Finalność lokalna nie jest gwarancją finalności publicznego blockchainu.

Jeżeli wejście nie jest ukończone, właściciel domyka je przed odbiorem zależnego
zadania. Indexer można rozwijać na logach z transakcji skryptowych, frontend
na jawnie oznaczonych fixture'ach; nie pozwala to zaliczyć pełnego pionu.

## 3. Kroki i zadania

Ścieżki względem katalogu repozytorium. W4-xx to przyrosty zadań Txxx.

### P4.1 — Adam: logi, projekcje i potwierdzanie poleceń

**Wymagania:** REQ-002, REQ-004, REQ-005, REQ-006, REQ-008, REQ-012.
**Przegląd:** Mikołaj dla Javy/SQL, Robert dla scenariusza reorgu.

- [ ] W4-01 [P] [Plan:4.1] T019: dodać `backend/src/main/java/pl/pz/supplyblockchain/infrastructure/blockchain/ChainEventIndexer.java` i potrzebne porty. Odczytywać logi `BatchCreated`, `StageCompleted`, `StageRejected` od bloku wdrożenia, wyłącznie właściwego kontraktu; porządek `(blockNumber, transactionIndex, logIndex)`, identyfikacja deploymentu i deduplikacja eventów.
- [ ] W4-02 [Plan:4.1] T019: dodać repozytoria projekcji/checkpointu w `backend/src/main/java/pl/pz/supplyblockchain/infrastructure/persistence/` i nową migrację w `backend/src/main/resources/db/migration/`, uzgodnioną z Mikołajem. Zapisywać eventy, projekcje, wynik skorelowanego polecenia i checkpoint atomowo; `StageRejected` daje `REJECTED` bez przesunięcia etapu. `CONFIRMED` nie wynika z samego `202`, wysłania ani receipt odczytanego przez worker.
- [ ] W4-03 [Plan:4.1] T019/T024 (przygotowanie): podłączyć `backend/src/main/java/pl/pz/supplyblockchain/application/BatchQueryService.java` i odczyt statusu do rzeczywistych projekcji/freshness zamiast prowizorycznego widoku poleceń. Przy zmianie hash bloku cofnąć niekanoniczne eventy/projekcje i uzgodnić statusy poleceń w oknie ponownego skanu. Pełne publiczne API historii i paginacja pozostają na tygodnie 6–7.

### P4.2 — Mikołaj: sender i minimalny worker z Kubo

**Wymagania:** REQ-001, REQ-002, REQ-004, REQ-005, REQ-006, REQ-007, REQ-012, REQ-013, REQ-014.
**Przegląd:** Adam dla transakcji, Kuba dla użycia adaptera Kubo.

- [ ] W4-04 [Plan:4.2] T017: dodać `backend/src/main/java/pl/pz/supplyblockchain/infrastructure/blockchain/RelayerTransactionSender.java`, wykorzystując istniejące `Eip1559TransactionSigner.java`, `CompleteStageCallEncoder.java`, `RelayerFeePolicy.java` i porty prób. Obsłużyć `createBatch` i etap 0; jeden writer/nonce, EIP-1559 lokalne, trwały zapis podpisanych bajtów i obliczonego tx hash **przed** `eth_sendRawTransaction`, uzgadnianie po timeout. Bez wrapperowego `.send()`, `DefaultGasProvider` i fee-bump.
- [ ] W4-05 [Plan:4.2] T016/T018: dodać `backend/src/main/java/pl/pz/supplyblockchain/infrastructure/queue/RelayerWorker.java`. Przejąć zadanie przez mechanizm z W3-06, pinować dokładne bajty JCS przez istniejący port `ReportStorage`, zapisać CID i checkpoint kroku, wykonać preflight oraz wysłać przez W4-04. Dla CREATE_BATCH pominąć raport/IPFS; dla STAGE sprawdzić stan, deadline i aktualność rejestru stacji. I/O poza długą transakcją DB.
- [ ] W4-06 [Plan:4.2] T018/T021 (przygotowanie): domknąć odzysk po restarcie, kontrolowany retry błędów przejściowych i jawne FAILED dla trwałego błędu według protokołu. Po nieznanym wyniku RPC szukać wcześniejszego tx hash i ewentualnie wysłać te same bajty, bez nowej operacji. Pozytywny raport etapu 2 bez approval zatrzymać jako `WAITING_FOR_QA` po pinowaniu, bez rezerwacji nonce i bez blokowania workera; negatywny raport realizuje `StageRejected` zgodnie z regułami kontraktu.

### P4.3 — Kuba: rzeczywiste odczyty i statusy UI

**Wymagania:** REQ-002, REQ-009, REQ-010 (przygotowanie), REQ-012.
**Przegląd:** Robert dla UX, Mikołaj dla kontraktu API.

- [ ] W4-07 [P] [Plan:4.3] T022: podłączyć `frontend/src/lib/api/client.ts` i `frontend/src/features/batches/` do lokalnych GET partii i numeru partii oraz `GET /commands/{id}` zgodnie z `shared/schemas/openapi.yaml`; odpytywanie kończyć na terminalnym wyniku i poprawnie obsłużyć `stale`, `401`, `403`, `404` i błąd sieci.
- [ ] W4-08 [Plan:4.3] T022: w `frontend/src/features/operator/` podłączyć formularz utworzenia partii z SIWE/Bearer i `Idempotency-Key`. Zachować klucz dla ponowienia tego samego zamiaru; nie pokazywać `202` jako gotowej partii. Prezentować postęp polecenia, potwierdzony etap 0, oczekiwanie QA i jawny błąd.
- [ ] W4-09 [Plan:4.3] T032/T025 (przygotowanie): sprawdzić lokalny portfel i URL/QR z telefonu przez HTTPS; w `frontend/README.md` opisać osiągalny adres aplikacji, RPC i gateway oraz bezpieczne użycie kont Anvila. Oznaczyć brak wdrożenia Amoy i brak gotowego paszportu; nie budować przełącznika środowisk Amoy.

### P4.4 — Robert: odbiór rzeczywistego pionu

**Wymagania:** REQ-001..008, REQ-011..014 (część QA tylko blokowanie bez approval).
**Przegląd:** Adam i Mikołaj dla dowodów chain/API, Kuba dla instrukcji.

- [ ] W4-10 [P] [Plan:4.4] T020: rozwinąć istniejące scenariusze w `simulator/scenarios/` i `simulator/src/supply_chain_simulator/runner.py` dla pionu partia → etap 0; czekać na potwierdzenie API i odczyt kontraktu z limitem czasu. Dry-run/accept-only pozostają osobnymi trybami i nie mogą zastępować pozytywnego wyniku E2E.
- [ ] W4-11 [Plan:4.4] T020/T027: po W4-02/W4-03/W4-06 wykonać pion na Anvil/Kubo/PostgreSQL bez ręcznych zmian DB. Sprawdzić zgodność CID/JCS/hash z eventem, 10 ponowień, restart po zapisie CID i po wysłaniu, niedostępne Kubo/RPC oraz odrzucenie złego podpisu/certyfikatu. Przypadek reorgu uzgodnić z Adamem; pozytywnego QA nie zaliczać bez niezależnego podpisu.
- [ ] W4-12 [Plan:4.4] T030 (przygotowanie): zapisać faktyczne wyniki i otwarte luki w `docs/planning/weekly/week4/week4-summary.md` dopiero po odbiorze. Dodać do `docs/guides/development.md` i `simulator/scenarios/README.md` komendy powtarzalnego uruchomienia, timeouty, reset i granice pokazanego pionu.

## 4. Podział na dni

| Dzień | Adam | Mikołaj | Kuba | Robert |
|---|---|---|---|---|
| Poniedziałek | W4-01: logi z transakcji skryptowych | W4-04: sender/próby | W4-07: klient GET i polling | W4-10: scenariusz pionu i oczekiwanie |
| Wtorek | W4-02: projekcje/checkpoint i korelacja | W4-04: podpis/send/reconciliation | W4-07: status/freshness/błędy | W4-10: seed, timeouty i statusy |
| Środa | W4-03: odczyt API z projekcji | W4-05: worker + Kubo + CREATE_BATCH/STAGE | W4-08: tworzenie partii | pierwsze W4-11 po gotowości pionu |
| Czwartek | W4-03: reorg i odzysk projekcji | W4-06: restart/retry/WAITING_FOR_QA | W4-08/W4-09: realne stany i telefon | W4-11: restart, duplikaty, awarie |
| Piątek | wspólne uzgodnienie event/status/kontrakt | stabilizacja minimalnego pionu | demonstracja UI na prawdziwych danych | W4-12: wspólny odbiór i raport |

## 5. Zależności, praca równoległa i granice plików

| Zadanie | Bezpośrednie zależności |
|---|---|
| W4-01 | W3-01, W3-03 |
| W4-02 | W4-01, W3-06 |
| W4-03 | W4-02 |
| W4-04 | W3-01, W3-05, W3-06; istniejący encoder/signer |
| W4-05 | W4-04, W3-04, W3-06; istniejący adapter Kubo |
| W4-06 | W4-05 |
| W4-07 | W3-08, W3-09; prawdziwa freshness w odbiorze czeka na W4-03 |
| W4-08 | W4-07, W3-05 |
| W4-09 | W3-07, W3-08 |
| W4-10 | W3-10; autor może przygotować scenariusz przed gotowym workerem |
| W4-11 | W4-03, W4-06, W4-10 |
| W4-12 | W4-08, W4-09, W4-11; niegotowe punkty wpisujemy jako otwarte |

Adam nie czeka z odczytem logów na sender: używa transakcji skryptowych.
Mikołaj nie pisze drugiego indexera; ustalają kontrakt zapisu event/status
i przydział kolejnych numerów migracji **przed** ich tworzeniem.
Kuba nie ustawia `CONFIRMED` w UI na podstawie receipt ani mocka.
Robert nie oznacza scenariusza E2E jako zaliczonego w trybie accept-only.

## 6. Kryteria odbioru

1. Partia utworzona przez API/UI rzeczywiście istnieje w `getBatch`, a etap 0
   kończy się `nextStage=1`. Wynik potwierdzają event, projekcja i status polecenia.
2. CID jest niepusty i odczytywalny z Kubo; hash pobranych kanonicznych bajtów
   odpowiada `reportHash` w podpisie oraz `StageCompleted`.
3. **10 identycznych ponowień** daje to samo `commandId` i dokładnie jeden
   efekt produkcyjny. Podwójne przetworzenie logu nie powiela projekcji.
4. Restart po pinowaniu wznawia zapisany krok. Restart/timeout po wysłaniu
   nie tworzy nowej operacji, tylko uzgadnia zapisany hash i te same bajty tx.
5. Dwa polecenia różnych partii nie powodują kolizji nonce relayera.
   Niedostępność Kubo/RPC nie prowadzi do pozornego `CONFIRMED`.
6. Snapshot/revert i alternatywne bloki na Anvilu nie pozostawiają usuniętego
   zdarzenia jako potwierdzonego w projekcji/statusie. Okno skanu jest konfigurowalne;
   głębszy rollback wymaga jawnego zatrzymania/diagnozy zamiast sukcesu.
7. Kontrola bez approval pozostaje w `WAITING_FOR_QA` i nie zajmuje nonce.
   Sprawdzenie tego punktu wymaga przygotowania partii na etapie 2; samego
   przejścia 0→1 nie przedstawiamy jako dowodu ukończenia QA.
8. UI pokazuje rzeczywiste pending/confirmed/failed i freshness; telefon działa
   na osiągalnym HTTPS. Brak telefonu lub integracji zapisujemy jako brak pokrycia.

Istniejące testy autorów i kontrole build/format/type-check pozostają bramką PR.
Do odbioru pionu potrzebne są realne usługi lokalne; nie zaliczamy pominiętych
testów Dockerowych jako sprawdzonej integracji.

## 7. Requirement Mapping

| REQ ID | Plan Items / zadania | Dowód lub dalszy odbiór |
|---|---|---|
| REQ-001 | P4.2/P4.4 / W4-05, W4-11 | realny deploy/role i odtwarzalny pion |
| REQ-002 | P4.1/P4.2/P4.3 / W4-02, W4-04, W4-05, W4-08 | createBatch i potwierdzona projekcja/API |
| REQ-003 | P4.4 / W4-10, W4-11 | JCS/hash/CID zgodne w symulatorze, Kubo i evencie |
| REQ-004 | P4.1/P4.2/P4.4 / W4-01, W4-05, W4-11 | podpis/deadline/rejestr i brak podwójnego efektu |
| REQ-005 | P4.1/P4.2/P4.4 / W4-02, W4-06, W4-11 | etap 0 i blokowanie kontroli bez approval; pełny QA tyg. 5–6 |
| REQ-006 | P4.1/P4.2/P4.4 / W4-02, W4-04..W4-06, W4-11 | lease, trwałe próby, nonce i idempotencja |
| REQ-007 | P4.2/P4.4 / W4-05, W4-11 | Kubo → CID → event → porównanie hash |
| REQ-008 | P4.1 / W4-01..W4-03 | checkpoint, kanoniczna projekcja i reorg; historia/paginacja T024 tyg. 6–7 |
| REQ-009 | P4.3 / W4-07, W4-08 | prawdziwe statusy UI; pełna kolejka/panel QA T021/T023 tyg. 5–6 |
| REQ-010 | P4.3 / W4-09 | lokalny HTTPS/QR; publiczny paszport T025 w tyg. 7 |
| REQ-011 | P4.4 / W4-10, W4-11 | rzeczywisty runner z oczekiwaniem na potwierdzenie |
| REQ-012 | P4.1/P4.2/P4.3/P4.4 / W4-03, W4-06, W4-07, W4-11 | restart, timeout, rollback i jawne błędy |
| REQ-013 | P4.2/P4.4 / W4-05, W4-11 | aktualność stacji w preflight i negatywne certyfikaty |
| REQ-014 | P4.2/P4.4 / W4-06, W4-11 | brak zamknięcia QA bez drugiego klucza; pełny pozytywny pion tyg. 5–6 |

## 8. Ryzyka i przekazanie do tygodnia 5

Największe ryzyko to skupienie sendera i workera u Mikołaja. Priorytetem są
CREATE_BATCH i etap 0, nie dokładanie drugiego storage czy sieci. Adam prowadzi
projekcje, Robert scenariusze i diagnozę, Kuba UI — bez równoległego przepisywania
adapterów backendu.

Do tygodnia 5 przekazujemy działający minimalny pion, wyniki restartu/reorgu,
listę braków workera i kontrakt kolejki QA. Kolejne kroki: T018/T019
(domknięcie), T021 (kandydaci/approval), T022 (panel operatora).
Nie nazywamy tego pełnym MVP, dopóki kontrola nie działa z dwoma niezależnymi
podpisami, a paszport i pełna macierz odbioru nie są ukończone.
