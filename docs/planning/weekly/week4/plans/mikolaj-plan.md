# Mikołaj — tydzień 4

**Cel:** pierwszy lokalny sender i worker, które rzeczywiście wykonują
utworzenie partii oraz etap 0, bez gubienia poleceń przy restarcie.
**Status:** plan, nie raport wykonania.
Plan nadrzędny: [tydzień 4](../week4-plan.md), sekcja P4.2.
Wejście: [Twój tydzień 3](../../week3/plans/mikolaj-plan.md).
Zakres: [wariant 1 / DEC-17](../../../../decisions/local-blockchain-scope.md).

## Zadania

- [ ] W4-04 [Plan:4.2] T017: dodać `RelayerTransactionSender` z użyciem
  istniejącego signera, encodera, polityki EIP-1559 i portów prób.
  Jeden writer/nonce; podpisane bajty i wyliczony hash zapisać przed send.
  Po timeout uzgadniać tę samą próbę, bez nowej operacji i fee-bump.
- [ ] W4-05 [Plan:4.2] T016/T018: dodać `RelayerWorker` na mechanizmie lease
  z tygodnia 3. Dla CREATE_BATCH pominąć IPFS; dla STAGE pinować dokładne JCS
  przez istniejący `ReportStorage`, zapisać CID/krok, sprawdzić stan, deadline
  i rejestr, wykonać preflight oraz wysłać transakcję.
- [ ] W4-06 [Plan:4.2] T018/T021: domknąć odzysk, kontrolowany retry błędów
  przejściowych i jawny wynik trwałego błędu. Nieznany wynik RPC uzgadniać
  po zapisanym hash, ewentualnie wysyłając te same bajty.
  Pozytywny etap 2 bez approval zatrzymać jako `WAITING_FOR_QA`, bez zajęcia
  nonce i blokowania workera; negatywny raport realizuje `StageRejected`.

## Plan dni

| Dzień | Co robisz | Wynik |
|---|---|---|
| Poniedziałek | W4-04: sender i trwałe próby | bezpieczny zapis przed wysyłką |
| Wtorek | W4-04: podpis/send/reconciliation | rzeczywiste transakcje Anvila |
| Środa | W4-05: worker, Kubo, CREATE_BATCH/STAGE | minimalny pion gotowy do integracji |
| Czwartek | W4-06: restart/retry/WAITING_FOR_QA | odzysk i bezpieczna bramka QA |
| Piątek | stabilizacja z Adamem i Robertem | partia i etap 0 potwierdzone przez indexer |

## Pliki i granice odpowiedzialności

Ścieżki względem repozytorium:
`backend/src/main/java/pl/pz/supplyblockchain/infrastructure/blockchain/RelayerTransactionSender.java`,
istniejące `Eip1559TransactionSigner.java`, `CompleteStageCallEncoder.java`,
`RelayerFeePolicy.java`, porty/próby w `application/ports/` i persistence
oraz `infrastructure/queue/RelayerWorker.java`.

Kubo już istnieje — wpinasz adapter, nie tworzysz drugiego.
I/O wykonuj poza długą transakcją DB. Atomowe potwierdzanie przez indexer
uzgodnij z Adamem; worker nie ogłasza sukcesu na podstawie samego receipt.

## Zależności i odbiór

W4-04 wymaga W3-01/W3-05/W3-06. W4-05 wymaga W4-04, W3-04/W3-06
i istniejącego Kubo; W4-06 następuje po W4-05.
Przegląd: Adam dla transakcji, Kuba dla użycia adaptera Kubo.

Odbiór: realne CREATE_BATCH i etap 0, trwały CID/próba, bezpieczny restart
po pinowaniu i wysyłce. Dwie partie nie kolidują nonce. Brak Kubo/RPC
nie daje `CONFIRMED`. Przygotowany etap 2 bez approval pozostaje w kolejce QA.
Kontrole według [testing](../../../../guides/testing.md).
Wymagania: REQ-001/002/004/005/006/007/012/013/014; mapowanie w planie wspólnym.

**Poza zakresem:** Pinata, backend Amoy, wrapperowe `.send()`,
`DefaultGasProvider`, fee-bump i pełne API/panel approval.
