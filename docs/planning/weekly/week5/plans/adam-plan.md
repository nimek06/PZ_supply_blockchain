# Adam — tydzień 5

**Cel:** kanoniczna historia etapów dostępna w GUI oraz poprawny lokalny deploy.
**Status:** plan, nie raport wykonania.
Plan nadrzędny: [tydzień 5](../week5-plan.md), sekcja P5.1.
Wejście: [Twój tydzień 4](../../week4/plans/adam-plan.md) i
[podsumowanie W4](../../week4/week4-summary.md).
Zakres: [wariant 1 / DEC-17](../../../../decisions/local-blockchain-scope.md).

## Zadania

- [ ] W5-01 [Plan:4.1] T019: sprawdzić na rzeczywistych `StageCompleted` etapów 0 i 1 kolejność, deduplikację, projekcje i korelację poleceń. W rozszerzeniu sprawdzić `StageRejected`: `CONFIRMED`/`outcome=REJECTED` bez przesunięcia etapu.
- [ ] W5-02 [Plan:6.1] T024 (część przyspieszona): dodać publiczny `GET /api/v1/batches/{batchId}/history` według OpenAPI: kanoniczne etapy dla właściwego deploymentu/partii, kolejność bloku/transakcji/logu, wymagane pola, freshness i paginacja z obsługą nieaktualnego kursora po reorgu. Nie ujawniać prywatnych danych. Przetestować pustą i wielostronicową historię, odrzucenia, nieznaną partię oraz usunięcie cofniętego zdarzenia.
- [ ] W5-03 [Plan:3.1/4.1] T013/T019: poprawić lokalny deploy, aby testowy operator otrzymał `OPERATOR_ROLE`; zachować rozdział ról i powtarzalność. Z Mikołajem uzgodnić włączenie workera, z Robertem odbiór harnessu.

## Plan dni

| Dzień | Co robisz | Wynik |
|---|---|---|
| Poniedziałek | W5-03 i przegląd poprawki signera | role i odblokowanie transakcji |
| Wtorek | W5-02: odczyt historii i uzgodnienie DTO z Kubą | kontrakt dla GUI |
| Środa | W5-01/W5-02: eventy, paginacja i testy | dwa rzeczywiste wpisy |
| Czwartek | W5-02: reorg i historia z Robertem | brak cofniętego wpisu |
| Piątek | przegląd ról, historii i pokazu | wyniki oraz przekazanie |

## Pliki i granice odpowiedzialności

`backend/src/main/java/pl/pz/supplyblockchain/infrastructure/blockchain/ChainEventIndexer.java`,
`backend/src/main/java/pl/pz/supplyblockchain/infrastructure/persistence/`,
porty, `application/`, `api/`, testy indexera i `contracts/script/deploy-local.sh`.
Wykorzystaj `chain_events`/`stage_projections` i `shared/schemas/openapi.yaml`;
DTO i nowe migracje uzgodnij z Mikołajem, użycie API z Kubą. Nie pisz
drugiego workera ani API QA.

## Zależności i odbiór

W5-01/W5-02 można przygotować na transakcjach skryptowych; odbiór pokazu
wymaga W5-04/W5-05 i scenariusza Roberta W5-10.
Przegląd: Mikołaj dla Javy/SQL, Robert dla reorgu.

Odbiór: API historii zwraca dwa prawdziwe etapy we właściwej kolejności,
strony są stabilne, cofnięty event znika; `nextStage=2` i deploy nadaje
właściwe role. Kontrole według [testing](../../../../guides/testing.md).
Wymagania: REQ-001/002/005/006/008/012; mapowanie w planie wspólnym.

**Poza zakresem:** indexer Amoy, pełna weryfikacja raportu w paszporcie i panel QA.
