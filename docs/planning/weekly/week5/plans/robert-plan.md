# Robert — tydzień 5

**Cel:** powtarzalna demonstracja tworzenia partii i jej historii w GUI.
**Status:** plan, nie raport wykonania.
Plan nadrzędny: [tydzień 5](../week5-plan.md), sekcja P5.4.
Wejście: [Twój tydzień 4](../../week4/plans/robert-plan.md) i
[podsumowanie W4](../../week4/week4-summary.md).
Zakres: [wariant 1 / DEC-17](../../../../decisions/local-blockchain-scope.md).

## Zadania

- [ ] W5-10 [Plan:4.1] T020: przygotować osobne kroki symulatora dla etapów 0 i 1 na `--batch-number` utworzonej wcześniej partii, bez tworzenia drugiej partii. Obecny walidator wymaga startu od 0: dostosuj go i runner bez osłabiania pełnych scenariuszy, sprawdź stan partii przed etapem 1 oraz dodaj testy kolejności i błędnego stanu. Zapewnić świeży certyfikat, oczekiwanie na projekcję/stan kontraktu, timeout i prostą komendę do demonstracji. QA waiting/fail to rozszerzenie.
- [ ] W5-11 [Plan:4.1/7.1] T020/T027: w rzeczywistej przeglądarce utworzyć partię, uruchomić symulator dla jej numeru, odświeżyć widok po każdym kroku i porównać dwa wpisy historii z eventami, CID/JCS/hash i `nextStage=2`. Bez ręcznej zmiany DB i bez zastępowania workera przez `cast`. Ponowienia, restart, awarie i reorg z Adamem wykonać po zabezpieczeniu pokazu; zapisać wyniki lub blokady.
- [ ] W5-12 [Plan:8.1] T030: przygotować instrukcję krok po kroku: usługi, backend, frontend, SIWE, numer z GUI, komendy etapów, oczekiwane wyniki i bezpieczne zatrzymanie. Uwzględnić macOS/Linux i Windows przez WSL2, zaznaczyć faktycznie sprawdzone platformy. Po odbiorze zapisać wyniki, timeouty, ostrzeżenia resetu i otwarte luki w podsumowaniu.

## Plan dni

| Dzień | Co robisz | Wynik |
|---|---|---|
| Poniedziałek | W5-10: scenariusze dla numeru z GUI | ten sam produkt w każdym kroku |
| Wtorek | W5-10: osobne etapy i timeouty | prowadzący kontroluje tempo pokazu |
| Środa | W5-11: pierwsza próba GUI | rzeczywiste potwierdzenia |
| Czwartek | W5-11: pokaz, awarie i reorg | dowody lub blokady |
| Piątek | W5-12: instrukcja, odbiór i raport | powtarzalna prezentacja |

## Pliki i granice odpowiedzialności

`simulator/scenarios/`, `simulator/src/supply_chain_simulator/runner.py`,
`simulator/scenarios/README.md`, `docs/guides/development.md` i przyszłe
`docs/planning/weekly/week5/week5-summary.md`.
Używaj realnych usług, nie pisz drugiego workera/indexera i nie zmieniaj
ręcznie statusów w DB. Diagnozy przekazuj właścicielom bez sekretów.
Przed resetem Anvila lub bazy sprawdź, czy nie usuwa potrzebnych danych.

## Zależności i odbiór

W5-10 można przygotować przed workerem. W5-11 wymaga W5-01..W5-05,
W5-07..W5-10; W5-06 jest potrzebne tylko do rozszerzenia QA.
W5-12 zbiera wyniki pokazu i wykonanych testów.
Przegląd: Adam i Mikołaj dla chain/API, Kuba dla instrukcji.

Odbiór: partia utworzona z GUI, dwa rzeczywiste wpisy historii po osobnych
krokach symulatora, `nextStage=2` i zachowanie danych po reloadzie.
QA, ponowienia, restart i reorg mają wyniki albo są jawnie otwarte.
Kontrole według [testing](../../../../guides/testing.md).
Wymagania: REQ-002..008/011..014 (QA częściowo).

**Poza zakresem:** podpis z panelu QA, pełna partia 0..3, paszport i Amoy.
