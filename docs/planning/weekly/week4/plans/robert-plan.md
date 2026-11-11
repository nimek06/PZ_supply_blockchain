# Robert — tydzień 4

**Cel:** udokumentowany lokalny pion partia → etap 0 z rzeczywistym
potwierdzeniem w łańcuchu, projekcji i API.
**Status:** plan, nie raport wykonania.
Plan nadrzędny: [tydzień 4](../week4-plan.md), sekcja P4.4.
Wejście: [Twój tydzień 3](../../week3/plans/robert-plan.md).
Zakres: [wariant 1 / DEC-17](../../../../decisions/local-blockchain-scope.md).

## Zadania

- [ ] W4-10 [Plan:4.4] T020: rozwinąć istniejące scenariusze i runner dla
  utworzenia partii i etapu 0. Czekać na potwierdzenie API oraz odczyt
  kontraktu z limitem czasu. Dry-run/accept-only pozostają odrębnymi trybami,
  nie zastępują pozytywnego wyniku E2E.
- [ ] W4-11 [Plan:4.4] T020/T027: po gotowości indexera i workera wykonać
  pion bez ręcznych zmian DB. Sprawdzić JCS/CID/hash/event, 10 ponowień,
  restart po CID i po wysłaniu, niedostępne Kubo/RPC, zły podpis/certyfikat.
  Z Adamem sprawdzić reorg; pozytywnego QA nie zaliczać bez drugiego klucza.
- [ ] W4-12 [Plan:4.4] T030: po odbiorze zapisać rzeczywiste komendy, wyniki,
  timeouty, reset i otwarte luki w podsumowaniu tygodnia 4 oraz instrukcjach.
  Rozdzielić potwierdzony etap 0 od jeszcze nieukończonego pełnego MVP.

## Plan dni

| Dzień | Co robisz | Wynik |
|---|---|---|
| Poniedziałek | W4-10: scenariusz pionu i oczekiwanie | gotowy przebieg do integracji |
| Wtorek | W4-10: seed, timeouty, statusy | powtarzalny runner |
| Środa | pierwsze W4-11 po gotowości workera/projekcji | rzeczywista partia i etap 0 |
| Czwartek | W4-11: restart, duplikaty, awarie i reorg | dowody odporności albo jawne braki |
| Piątek | W4-12: odbiór wspólny i raport | przekazanie do tygodnia 5 |

## Pliki i granice odpowiedzialności

Ścieżki względem repozytorium:
`simulator/scenarios/`, `simulator/src/supply_chain_simulator/runner.py`,
`simulator/scenarios/README.md`, `docs/guides/development.md` i przyszłe
`docs/planning/weekly/week4/week4-summary.md`.

Używaj realnych Anvil/Kubo/PostgreSQL. Nie zmieniaj ręcznie statusów w DB
i nie pisz drugiego workera/indexera. Diagnozę problemów przekazuj
właścicielom modułów z konkretnym commandId/tx hash, bez sekretów.

## Zależności i odbiór

W4-10 wymaga W3-10, ale może powstawać przed workerem.
W4-11 wymaga W4-03/W4-06/W4-10.
W4-12 zbiera także wyniki UI/telefonu z W4-08/W4-09; braki pozostają otwarte.
Przegląd: Adam i Mikołaj dla chain/API, Kuba dla instrukcji.

Odbiór: `getBatch.nextStage=1`, zgodny CID/hash i potwierdzone polecenie;
10 ponowień daje ten sam commandId i jeden efekt. Restart po CID/send
nie gubi ani nie powiela operacji. Reorg nie pozostawia fałszywego potwierdzenia.
Przygotowany etap 2 bez approval zatrzymuje się w `WAITING_FOR_QA`;
sam etap 0 nie dowodzi ukończenia QA.
Kontrole według [testing](../../../../guides/testing.md).
Wymagania: REQ-001..008/011..014 (QA częściowo); mapowanie w planie wspólnym.

**Poza zakresem:** pełne zamknięcie partii 0..3, pozytywny pion QA bez
gotowego approval, paszport i testy aplikacji na Amoy.
