# Adam — tydzień 4

**Cel:** lokalny indexer i kanoniczne projekcje, które potwierdzają rzeczywiste
utworzenie partii i etap 0.
**Status:** plan, nie raport wykonania.
Plan nadrzędny: [tydzień 4](../week4-plan.md), sekcja P4.1.
Wejście: [Twój tydzień 3](../../week3/plans/adam-plan.md).
Zakres: [wariant 1 / DEC-17](../../../../decisions/local-blockchain-scope.md).

## Zadania

- [ ] W4-01 [Plan:4.1] T019: dodać lokalny `ChainEventIndexer` dla logów
  `BatchCreated`, `StageCompleted`, `StageRejected`, od bloku wdrożenia.
  Filtrować kontrakt/deployment, zachować kolejność
  `(blockNumber, transactionIndex, logIndex)` i deduplikację.
- [ ] W4-02 [Plan:4.1] T019: dodać persistence projekcji/checkpointu i nową
  migrację uzgodnioną z Mikołajem. Eventy, projekcje, wynik skorelowanego
  polecenia i checkpoint zapisywać atomowo. Odrzucenie oznacza `REJECTED`,
  bez przesunięcia etapu; samo wysłanie/receipt workera nie daje `CONFIRMED`.
- [ ] W4-03 [Plan:4.1] T019/T024: podłączyć rzeczywiste projekcje/freshness
  do `BatchQueryService` i statusu polecenia. Po zmianie hash bloku usunąć
  niekanoniczne skutki i uzgodnić statusy w oknie ponownego skanu.

## Plan dni

| Dzień | Co robisz | Wynik |
|---|---|---|
| Poniedziałek | W4-01: logi z transakcji skryptowych | odczyt niezależny od gotowości sendera |
| Wtorek | W4-02: checkpoint, projekcje i korelacja | trwały, atomowy zapis |
| Środa | W4-03: podłączenie odczytu API | rzeczywista freshness i status |
| Czwartek | W4-03: reorg i odzysk | brak fałszywych potwierdzeń |
| Piątek | odbiór event/status/getBatch z zespołem | potwierdzona partia i etap 0 |

## Pliki i granice odpowiedzialności

Ścieżki względem repozytorium:
`backend/src/main/java/pl/pz/supplyblockchain/infrastructure/blockchain/ChainEventIndexer.java`,
porty w `application/ports/`, adaptery w `infrastructure/persistence/`,
`application/BatchQueryService.java`, odczyt statusu polecenia
i nowe migracje w `backend/src/main/resources/db/migration/`.

Przed zmianami uzgodnij z Mikołajem porty, numery migracji i właściciela
zapisu statusów. Nie pisz drugiego workera ani sendera.

## Zależności i odbiór

W4-01 wymaga W3-01/W3-03, W4-02 także W3-06, W4-03 wymaga W4-02.
Do pracy na logach wystarczą transakcje skryptowe; wspólny pion czeka
na sender/worker Mikołaja. Przegląd: Mikołaj dla Javy/SQL,
Robert dla scenariusza reorgu.

Odbiór: `latest` + 1 potwierdzenie, deduplikacja logów, checkpoint atomowy
z projekcją, `StageRejected` bez przesunięcia i poprawny odczyt API.
Snapshot/revert z alternatywnymi blokami nie pozostawia usuniętego eventu
jako potwierdzonego. Głębszy rollback niż obsługiwane okno wymaga jawnej diagnozy.
Kontrole według [testing](../../../../guides/testing.md).
Wymagania: REQ-002/004/005/006/008/012; mapowanie w planie wspólnym.

**Poza zakresem:** indexer Amoy, `finalized`, pełne API historii/paginacja
(tygodnie 6–7) i dodatkowe demo Polygon (tydzień 10).
