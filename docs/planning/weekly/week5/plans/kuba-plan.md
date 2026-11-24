# Kuba — tydzień 5

**Cel:** pokazywalne GUI: utworzenie partii oraz publiczna historia rzeczywistych etapów.
**Status:** plan, nie raport wykonania.
Plan nadrzędny: [tydzień 5](../week5-plan.md), sekcja P5.3.
Wejście: [Twój tydzień 4](../../week4/plans/kuba-plan.md).
Zakres: [wariant 1 / DEC-17](../../../../decisions/local-blockchain-scope.md).

## Zadania

- [ ] W5-07 [Plan:5.1] T022: domknąć ręczne wyszukiwanie partii po numerze, widok etapów i responsywność na danych API; obsłużyć loading, brak danych, freshness/stale oraz błędy.
- [ ] W5-08 [Plan:5.1] T022: domknąć tworzenie z SIWE/Bearer i `Idempotency-Key`, polling prywatnego statusu oraz link do publicznego widoku po `CONFIRMED`. Pokaż numer, pending i `FAILED`; samo `202` nie oznacza gotowej partii.
- [ ] W5-09 [Plan:6.1] T025 (część przyspieszona): w istniejącym widoku paszportu wyświetlić historię z W5-02: etap, wynik, CID, hash raportu/tx i numer bloku. Obsłużyć puste dane, błąd, freshness, odświeżanie i kolejne strony; widok po numerze działa bez portfela. Nie wyliczaj historii z `nextStage` ani nie pokazuj niezweryfikowanej integralności jako potwierdzonej.

## Plan dni

| Dzień | Co robisz | Wynik |
|---|---|---|
| Poniedziałek | W5-08: tworzenie i status | rzeczywiste polecenie operatora |
| Wtorek | W5-09: widok historii i stany | puste dane, błąd, paginacja |
| Środa | W5-07/W5-09: odczyt API Adama | prawdziwe wpisy i odświeżanie |
| Czwartek | integracja widoku i linku po utworzeniu | przejście przez GUI |
| Piątek | pokaz z lokalnym API i Robertem | tworzenie oraz dwa etapy |

## Pliki i granice odpowiedzialności

`frontend/src/lib/api/client.ts`, `frontend/src/features/batches/`,
`frontend/src/features/operator/` i `frontend/src/features/passport/`.
Korzystaj z `shared/schemas/openapi.yaml`; odczyt historii uzgodnij z Adamem,
zmiany kontraktu z Mikołajem.
Nie ujawniaj prywatnych danych i nie traktuj fixture'ów jako wyniku E2E.

## Zależności i odbiór

W5-08 wymaga działającego deployu i workera W5-03/W5-05;
odczyt W5-09 wymaga API W5-02. Przegląd: Robert dla UX, Mikołaj dla API.

Odbiór: tworzenie rzeczywistej partii, przejście do jej publicznego widoku,
dwa wpisy etapów aktualizowane po odświeżeniu i zachowane po reloadzie;
czytelne pending, błędy, freshness oraz responsywność.
Testy komponentów i build/type-check według
[testing](../../../../guides/testing.md).
Wymagania: REQ-002/008/009/010 (częściowo)/012; mapowanie w planie wspólnym.

**Poza zakresem:** podpis `StageApproval`, pełny panel QA, weryfikacja
integralności raportu i Amoy.
