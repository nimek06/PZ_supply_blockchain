# Robert — tydzień 3

**Cel:** dopasować istniejący symulator do lokalnego przyjęcia i przeprowadzić
wspólny odbiór bez udawania wykonania on-chain.
**Status:** plan, nie raport wykonania.
Plan nadrzędny: [tydzień 3](../week3-plan.md), sekcja P3.4.
Zakres: [wariant 1 / DEC-17](../../../../decisions/local-blockchain-scope.md).
Kontynuacja: [tydzień 4](../../week4/plans/robert-plan.md).

## Zadania

- [ ] W3-10 [Plan:3.4] T009/T020: dopasować istniejący klient, runner,
  CLI i scenariusze do rzeczywistego OpenAPI. Zachować dry-run, accept-only
  i deterministyczny seed, bez nowego runnera.
- [ ] W3-11 [Plan:3.4] T020/T027: przez lokalne API uruchomić poprawne zgłoszenie
  oraz negatywne fixture'y profilu, podpisu i certyfikatu. Dopóki nie ma workera,
  poprawny przebieg kończy się na trwałym `202`, nie fikcyjnym potwierdzeniu.
  Sprawdzić ponowienia i odczyt polecenia po restarcie.
- [ ] W3-12 [Plan:3.4] T030: koordynować wspólny odbiór. Dopiero po wykonaniu
  zapisać komendy, rzeczywiste wyniki i braki w podsumowaniu tygodnia 3;
  przygotować przekazanie scenariuszy i blokad do tygodnia 4.

## Plan dni

| Dzień | Co robisz | Wynik |
|---|---|---|
| Poniedziałek | W3-10: porównanie scenariuszy z API | zgodny kontrakt klienta |
| Wtorek | W3-10: dry-run/accept-only, seed i tożsamości | odtwarzalne zgłoszenia |
| Środa | W3-11: poprawne i negatywne przyjęcie | rzeczywiste statusy HTTP i commandId |
| Czwartek | W3-11: restart backendu i ponowienia | dowód trwałości i idempotencji |
| Piątek | W3-12: wspólny odbiór, przegląd UI/QR Kuby | wyniki oraz lista otwartych punktów |

## Pliki i granice odpowiedzialności

Ścieżki względem repozytorium:
`simulator/src/supply_chain_simulator/cli.py`, `client.py`, `runner.py`,
`simulator/scenarios/`, `simulator/scenarios/README.md`,
`docs/guides/development.md` oraz przyszłe
`docs/planning/weekly/week3/week3-summary.md`.
Utrzymujesz wspólne fixture'y; zmianę podpisanych pól uzgadniasz
z Adamem, Mikołajem i Kubą, nie dopasowujesz ich lokalnie tylko do Pythona.

## Zależności i odbiór

W3-10 można przygotowywać równolegle z resztą zespołu.
W3-11 wymaga W3-01, W3-04, W3-05 i W3-10.
W3-12 zbiera wyniki wszystkich zadań tygodnia; braki pozostają jawnie otwarte.
Przegląd: Mikołaj dla API, Kuba dla instrukcji uruchomienia.

Odbiór: poprawne przyjęcie jest trwałe, 10 identycznych ponowień zwraca ten sam
`commandId`, także po restarcie. Negatywne raporty/certyfikaty mają oczekiwany
jawny wynik. Dry-run/accept-only nie są dowodem wykonania etapu on-chain.
Kontrole według [testing](../../../../guides/testing.md); brak Dockera
nie oznacza zaliczenia integracji.
Wymagania: REQ-003/004/005/006/011/012/013; mapowanie w planie wspólnym.

**Poza zakresem:** nowy runner, pełny proces 0..3, API approval i scenariusze Amoy.
