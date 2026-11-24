# Plan tygodnia 5 — działający pokaz w GUI

**Data planu:** 2026-11-24. **Status:** plan do wykonania, nie raport wyników.
Podstawa: [plan projektu](../../plan.md), [podział zadań](../../team-split.md),
[podsumowanie tygodnia 4](../week4/week4-summary.md),
[protokół](../../../architecture/protocol.md) i
[wariant 1 / DEC-17](../../../decisions/local-blockchain-scope.md).

## Plany osobiste

- [Adam — indexer, API historii i lokalny deploy](plans/adam-plan.md)
- [Mikołaj — relayer, worker i przygotowanie QA](plans/mikolaj-plan.md)
- [Kuba — tworzenie partii i historia w GUI](plans/kuba-plan.md)
- [Robert — symulator, uruchomienie i odbiór pokazu](plans/robert-plan.md)

Plany osobiste rozwijają ten sam podział W5-01..W5-12. Przy zmianie zakresu
aktualizujemy plan wspólny i plan właściwej osoby.

## 1. Cel i granice

**Cel pokazu:** w przeglądarce operator loguje się portfelem, klika „Utwórz
partię”, widzi jej potwierdzenie, a następnie otwiera publiczny widok partii
i jej rzeczywistą historię. Symulator zgłasza dwa kolejne etapy **dla tej
samej partii**; po każdym zgłoszeniu prowadzący odświeża widok i pokazuje
nowe zdarzenie. Dane pochodzą z Anvila, Kubo i PostgreSQL, nie z fixture'ów.

W obecnym modelu „produkt” na pokazie to **partia rowerów**: backend nadaje
jej numer. Nie obiecujemy formularza nazwy, ceny ani pojedynczych egzemplarzy,
których obecny kontrakt tworzenia partii nie obsługuje. Zgłoszenie etapu robi
symulator stacji uruchomiony osobno, nie przycisk udający stację w GUI.

### Scenariusz pokazu — obowiązkowy

1. Uruchomić lokalne usługi, backend z workerem/indexerem i frontend;
   zalogować testowego operatora SIWE, wybrać prawdziwe API, nie tryb demo.
2. W GUI utworzyć nową partię, poczekać na `CONFIRMED` i pokazać jej numer.
   `202` to jedynie przyjęcie polecenia.
3. Otworzyć z GUI publiczny widok tej partii; początkowo historia etapów jest
   pusta. Symulator, uruchomiony na numerze z GUI, zgłasza etap 0; po jego
   potwierdzeniu odświeżyć widok i pokazać pierwszy wpis historii.
4. Osobnym krokiem symulator zgłasza etap 1 tej samej partii. Po potwierdzeniu
   odświeżyć widok: dwa wpisy we właściwej kolejności, `nextStage=2`,
   status, CID, hash raportu i transakcji oraz numer bloku.
5. Otworzyć ten sam publiczny widok po ponownym wpisaniu numeru lub przez link
   bez portfela. Odświeżenie przeglądarki nie może usunąć historii.

**Rozszerzenie po zaliczeniu pokazu:** pozytywny raport kontroli etapu 2
oczekuje w `WAITING_FOR_QA`; osobny negatywny przypadek może dać
`StageRejected`, `CONFIRMED`/`outcome=REJECTED` bez przesunięcia etapu.
Nie jest to ukończona kontrola. Pełny `POST approval`, podpis z panelu
i zamknięcie kontroli dwoma kluczami pozostają na tydzień 6.

Przesuwamy na tydzień 5 część T024/T025: publiczną, paginowaną historię
etapów i jej podstawowy widok. Pełna weryfikacja integralności raportu,
rozbudowany paszport i pozostałe prace T024/T025 zostają na tygodnie 6–7.
Endpoint historii zgodnie z obecnym OpenAPI zawiera etapy, nie event
`BatchCreated`: utworzoną partię pokazujemy jako informację o partii, a nie
zmyślony wpis zdarzenia. Nie obiecujemy daty zdarzenia, której obecne
projekcje nie przechowują.

Pracujemy na Anvil/Kubo/PostgreSQL. Bez Amoy, Pinaty, fee-bump, nowego runnera
i pełnego paszportu. Kolejka QA, jeśli powstanie w rozszerzeniu, korzysta
z istniejącego kontraktu OpenAPI, w tym paginacji kandydatów.
Lokalna polityka indexera: `latest`, 1 potwierdzenie, licząc blok eventu;
nie jest to gwarancja finalności publicznego łańcucha.

## 2. Wejście i uzgodnienia

- [Podsumowanie W4](../week4/week4-summary.md) wskazuje działające projekcje
  i ręcznie potwierdzony etap 0, ale nie pełny automatyczny pion przez workera.
- Mikołaj dostarczył trwałe przyjęcie, sender i worker; Adam deploy, indexer
  i projekcje; Kuba GUI, SIWE, tworzenie i odczyt partii; Robert symulator
  i sprawdzenie pionu. Kod istnieje, ale wynik GUI → worker → historia nie
  został jeszcze potwierdzony jako całość.
- `BatchController` zwraca podsumowanie partii, nie historię; obecny pasek
  etapów w GUI wylicza postęp z `nextStage`. `chain_events` przechowuje
  dane zdarzeń i pozycje, a `stage_projections` szczegóły etapów. OpenAPI
  opisuje `/api/v1/batches/{batchId}/history`, lecz endpoint wymaga implementacji.
- Walidator scenariuszy symulatora wymaga obecnie rozpoczęcia dodatnich kroków
  od etapu 0. Osobne uruchomienie etapu 1 wymaga więc dostosowania runnera
  i walidacji, nie tylko dopisania pliku YAML z samym etapem 1.
- Priorytet: W4-04 — signer używa przeciążenia transakcji typu `0x04` zamiast
  EIP-1559 `0x02`. Mikołaj poprawia i testuje; Adam przegląda transakcję.
- Lokalny deploy nie nadaje `OPERATOR_ROLE`, a harness nie włącza workera.
  Adam odpowiada za deploy/role, Mikołaj za ustawienia workera;
  Robert integruje i dokumentuje przebieg. Nie zakładamy, że harness E2E z W3
  już potwierdza transakcje.
- Przed zmianami persistence Adam i Mikołaj uzgadniają porty, numer migracji
  i właściciela zapisu statusów. Istniejących migracji nie przepisujemy.
- Kuba i Mikołaj uzgadniają użycie `shared/schemas/openapi.yaml`. Fixture'y
  mogą odblokować pracę UI, ale nie zastępują odbioru na rzeczywistym API.

Jeśli wejście nie jest gotowe, właściciel domyka je przed odbiorem zadania
zależnego. Wszelkie nieprzeprowadzone testy i przeglądy pozostają jawnie otwarte.

## 3. Kroki i zadania

Ścieżki względem repozytorium. W5-xx to przyrosty zadań Txxx.

### P5.1 — Adam: domknięcie indexera i lokalnego deployu

**Wymagania:** REQ-001, REQ-002, REQ-005, REQ-006, REQ-008, REQ-012.
**Przegląd:** Mikołaj dla Javy/SQL i transakcji, Robert dla scenariuszy.

- [ ] W5-01 [P] [Plan:4.1] T019: sprawdzić indexer i projekcje dla rzeczywistych `StageCompleted` etapów 0 i 1; zachować deduplikację, kolejność i atomową korelację z poleceniami. W rozszerzeniu sprawdzić także `StageRejected`: `CONFIRMED`/`outcome=REJECTED`, bez przesunięcia etapu.
- [ ] W5-02 [Plan:6.1] T024 (część przyspieszona): zaimplementować publiczny `GET /api/v1/batches/{batchId}/history` według istniejącego OpenAPI. Czytać wyłącznie kanoniczne potwierdzone zdarzenia etapów właściwego deploymentu/partii, w kolejności `(blockNumber, transactionIndex, logIndex)`; zwracać wymagane pola, freshness, `limit` i `nextCursor`, obsłużyć błędny/zdezaktualizowany kursor i reorg zgodnie z kontraktem. Nie ujawniać prywatnych commands ani podpisów. Testy: pusta historia, wiele zdarzeń (także `REJECTED`), strony bez pominięć/duplikatów, błędna partia i cofnięty event. Szczegóły integralności/paszportu pozostają na później.
- [ ] W5-03 [P] [Plan:3.1/4.1] T013/T019: domknąć lokalny deploy i harness: nadać właściwemu testowemu operatorowi `OPERATOR_ROLE`, zachować rozdzielenie ról oraz powtarzalność deployu. Uzgodnić z Mikołajem konfigurację włączenia workera i z Robertem sposób odbioru; nie używać prawdziwych kluczy.

### P5.2 — Mikołaj: sender, worker i początek API QA

**Wymagania:** REQ-002, REQ-004, REQ-005, REQ-006, REQ-007, REQ-009, REQ-012, REQ-013, REQ-014.
**Przegląd:** Adam dla transakcji/persistence, Kuba dla kontraktu API.

- [ ] W5-04 [P] [Plan:4.1] T017: poprawić `Eip1559TransactionSigner` według ustalenia W4. Test ma dekodować podpisaną transakcję jako typ `0x02` i sprawdzać chainId, nonce, gas limit, opłaty, adres i calldata; potwierdzić wysyłkę przez relayer na Anvilu. Zachować zapis bajtów/hash przed send i odzysk tej samej próby.
- [ ] W5-05 [Plan:4.1] T018: najpierw uruchomić i domknąć worker dla CREATE_BATCH i etapów 0–1: dokładne JCS/CID, preflight, retry i odzysk po restarcie. Następnie przygotować etap 2: pozytywny zatrzymać w `WAITING_FOR_QA` po pinowaniu, bez rezerwacji nonce i blokowania innych poleceń; negatywny wysłać do `StageRejected`. Receipt sam nie nadaje `CONFIRMED`; etap 2 jest rozszerzeniem pokazu.
- [ ] W5-06 [Plan:5.1] T021 (start, rozszerzenie po pokazie): udostępnić chronione `GET /api/v1/qa/candidates` i `GET /api/v1/qa/candidates/{commandId}` zgodnie z OpenAPI, paginacją i regułami dostępu. Zwracać dane raportu, CID, hash, deadline i domenę bez podpisów stacji, signed tx i tokenów. Sprawdzić dostęp QA/admina do listy, QA/admina lub właściciela do szczegółów oraz odmowę innym. `POST approval` i przejście po podpisie są zakresem tygodnia 6; brak W5-06 nie blokuje podstawowego pokazu etapów 0–1.

### P5.3 — Kuba: panel operatora na rzeczywistych danych

**Wymagania:** REQ-002, REQ-008, REQ-009, REQ-010 (częściowo), REQ-012.
**Przegląd:** Robert dla UX, Mikołaj dla API.

- [ ] W5-07 [P] [Plan:5.1] T022: domknąć ręczne wyszukiwanie partii po numerze, widok etapu i responsywny panel operatora z rzeczywistych danych API. Pokazać loading, pusty wynik, `stale`, błędy sieci i autoryzacji; nie przedstawiać fixture'ów jako wyniku integracji.
- [ ] W5-08 [Plan:5.1] T022: domknąć tworzenie partii z SIWE/Bearer i `Idempotency-Key` oraz polling prywatnego statusu. Po `CONFIRMED` pokazać numer i umożliwić otwarcie publicznego widoku tej samej partii; `202` ani receipt nie oznaczają ukończenia. Pokazać jawne pending/failed i bezpieczne ponowienie.
- [ ] W5-09 [Plan:6.1] T025 (część przyspieszona): w istniejącym `PassportPage` i kliencie API pokazać rzeczywistą historię z W5-02: etap, `COMPLETED`/`REJECTED`, CID, hash raportu i transakcji, blok, kolejność i freshness. Obsłużyć pustą historię, błąd, stale, odświeżanie i kolejne strony; publiczny widok działa bez portfela. Nie wyliczać historii z `nextStage`, nie mieszać danych demo z API i nie twierdzić, że hash raportu został zweryfikowany. Pełna weryfikacja integralności jest na tydzień 7.

### P5.4 — Robert: scenariusze i odbiór lokalny

**Wymagania:** REQ-002..008, REQ-011..014 (QA częściowo).
**Przegląd:** Adam i Mikołaj dla chain/API, Kuba dla instrukcji i UI.

- [ ] W5-10 [P] [Plan:4.1] T020: dostosować istniejące `simulator/scenarios/`, walidator i runner do zgłaszania **osobno** etapu 0 i etapu 1 dla numeru podanego przez `--batch-number`, bez tworzenia kolejnej partii. Zachować dotychczasową walidację pełnych scenariuszy; dla osobnego etapu 1 sprawdzić, że stan partii pozwala na ten etap, i dodać testy kolejności oraz błędnego stanu. Zapewnić aktualny certyfikat, oczekiwanie na właściwą projekcję/stan kontraktu i timeout; opracować prostą komendę do użycia w trakcie pokazu. W rozszerzeniu przygotować QA waiting/fail. `--dry-run` i accept-only nie są dowodem E2E.
- [ ] W5-11 [Plan:4.1/7.1] T020/T027: przeprowadzić scenariusz z sekcji 1 w rzeczywistej przeglądarce na Anvil/Kubo/PostgreSQL, z symulatorem na numerze z GUI, bez ręcznych zmian DB i transakcji `cast` zamiast workera. Sprawdzić rzeczywiste wpisy przed/po każdym etapie, odświeżenie strony i zgodność CID/JCS/hash/event. Ponowienia, restart, awarie i reorg z Adamem wykonać po zabezpieczeniu podstawowego pokazu; wyniki lub blokady zapisać osobno.
- [ ] W5-12 [Plan:8.1] T030 (przygotowanie): przygotować instrukcję pokazu krok po kroku: wymagania portfela/testowych kont, uruchomienie usług/backendu/frontendu, komendy etapów dla numeru z GUI, oczekiwane wyniki, bezpieczne zatrzymanie i ostrzeżenia przed resetem danych. Standardowe uruchomienie pokazu nie może po cichu resetować istniejącego Anvila ani bazy; reset to osobna jawna operacja. Uwzględnić macOS/Linux oraz Windows przez WSL2; podać faktycznie sprawdzone platformy, nie zakładać ich weryfikacji. Po rzeczywistym odbiorze utworzyć `docs/planning/weekly/week5/week5-summary.md`; nie wpisywać nieprzeprowadzonych testów jako zaliczonych.

## 4. Podział na dni

| Dzień | Adam | Mikołaj | Kuba | Robert |
|---|---|---|---|---|
| Poniedziałek | W5-03: role/deploy; przegląd signera | W5-04: poprawka i test typu tx | W5-08: tworzenie partii i status | W5-10: scenariusze dla numeru z GUI |
| Wtorek | W5-02: kontrakt i odczyt historii | W5-04/W5-05: send na Anvilu i worker | W5-09: widok historii, puste/błędy | W5-10: osobne etapy i timeouty |
| Środa | W5-01/W5-02: eventy, paginacja i testy | W5-05: pion etapów 0–1 | W5-07/W5-09: API, odświeżanie | W5-11: pierwsza próba GUI |
| Czwartek | W5-02: reorg i korekty historii | stabilizacja workera; W5-06 jeśli pokaz gotowy | integracja widoku i linku | W5-11: ponowny pokaz, awarie/reorg |
| Piątek | przegląd historii i ról | stabilizacja pionu | pokaz tworzenia i historii | W5-12: instrukcja, odbiór i raport |

## 5. Zależności i granice plików

| Zadanie | Bezpośrednie zależności |
|---|---|
| W5-01 | W4-01..W4-03; do odbioru rzeczywistych zdarzeń W5-05 |
| W5-02 | W4-02/W4-03 i istniejące OpenAPI; odbiór rzeczywistych etapów po W5-01/W5-05 |
| W5-03 | W3-01/W3-02; konfiguracja workera uzgodniona z W5-05 |
| W5-04 | W4-04; przegląd Adama |
| W5-05 | W5-04, W4-05/W4-06; do pełnego pionu W5-03 |
| W5-06 | W4-06, W5-05; rozszerzenie po podstawowym pokazie |
| W5-07 | W4-07/W4-08; prawdziwe dane z W5-01/W5-05 do odbioru |
| W5-08 | W5-07, W5-03, W5-05 |
| W5-09 | istniejący OpenAPI; rzeczywisty odczyt wymaga W5-02 |
| W5-10 | W4-10; można przygotować przed naprawą workera |
| W5-11 | W5-01..W5-05, W5-07..W5-10; W5-06 tylko dla rozszerzenia QA |
| W5-12 | W5-02, W5-08/W5-09, W5-11; braki wpisać jako otwarte |

Adam prowadzi indexer, projekcje i deploy; Mikołaj sender, worker i API QA.
Kuba prowadzi UI, Robert scenariusze i odbiór. Nie tworzymy równoległych
implementacji tych samych adapterów. Numer nowej migracji uzgadniamy przed
utworzeniem; zmiany DTO/OpenAPI uzgadniają Mikołaj i Kuba.

## 6. Kryteria odbioru

1. Prowadzący tworzy nową partię w rzeczywistym GUI i widzi `CONFIRMED` oraz
   numer; otwiera jej publiczną historię bez portfela.
2. Rzeczywisty sender wysyła transakcję EIP-1559 typu `0x02`; partia i etapy
   0–1 przechodzą przez worker, event, indexer i API, a `getBatch.nextStage=2`.
3. Po każdym osobnym kroku symulatora GUI pokazuje nowy wpis historii tej
   samej partii; po dwóch krokach są dwa poprawnie uporządkowane wpisy
   `COMPLETED` z CID/hash/tx/blokiem. Historia pozostaje po reloadzie i wejściu
   po numerze, obsługuje wiele stron i nie pokazuje cofniętych zdarzeń.
4. CID z Kubo daje kanoniczne bajty, których hash odpowiada raportowi i eventowi.
   Brak danych, błąd i `stale` są jawne; fixture'y nie zaliczają pokazu.
5. Autorzy uruchamiają testy modułów i build/type-check. Po pokazie weryfikujemy
   ponowienia, restart, awarie i reorg; niewykonane przypadki pozostają otwarte,
   a awaria nie może być prezentowana jako potwierdzenie.
6. Rozszerzenie QA zaliczamy osobno: `WAITING_FOR_QA` bez nonce oraz na
   innej partii `StageRejected` bez przesunięcia; lista/szczegóły kandydatów
   przestrzegają ról, jeśli W5-06 jest gotowe.

## 7. Requirement Mapping

| REQ ID | Zadania | Dowód lub dalszy odbiór |
|---|---|---|
| REQ-001 | W5-03, W5-11 | powtarzalny lokalny deploy i role |
| REQ-002 | W5-03..W5-05, W5-07/W5-08, W5-11 | rzeczywiste utworzenie partii i projekcja |
| REQ-003 | W5-05, W5-10/W5-11 | JCS/hash/CID i zgodność z eventem |
| REQ-004 | W5-05, W5-10/W5-11 | podpis, deadline, rejestr i etapy |
| REQ-005 | W5-01, W5-05/W5-06, W5-11 | kolejność etapów; QA waiting/odrzucenie jako rozszerzenie, pełne approval w tyg. 6 |
| REQ-006 | W5-04/W5-05, W5-08, W5-11 | trwałe próby, nonce i idempotencja |
| REQ-007 | W5-05, W5-11 | rzeczywisty Kubo/CID/hash |
| REQ-008 | W5-01/W5-02, W5-09/W5-11 | kanoniczna paginowana historia w GUI, rollback i freshness |
| REQ-009 | W5-06..W5-09 | operator; chroniony odczyt kandydatów jako rozszerzenie, podpis w tyg. 6 |
| REQ-010 | W5-09/W5-11 | podstawowy publiczny widok historii; pełny paszport i integralność później |
| REQ-011 | W5-10/W5-11 | scenariusze symulatora z rzeczywistym potwierdzeniem |
| REQ-012 | W5-02, W5-04/W5-05, W5-07/W5-08, W5-11 | odzysk, reorg, freshness i błędy |
| REQ-013 | W5-03, W5-05, W5-11 | role, rejestr stacji i preflight |
| REQ-014 | W5-05/W5-06, W5-10/W5-11 | blokada bez podpisu w rozszerzeniu QA; pełny odbiór dwóch kluczy w tyg. 6 |

## 8. Ryzyka i przekazanie do tygodnia 6

Ścieżka krytyczna pokazu: role i sender → worker → eventy → API historii →
GUI → odbiór. W5-06 i rozbudowana macierz awarii nie mogą wyprzedzić
działającego pokazu; ewentualne braki zapisujemy jawnie. Do tygodnia 6
przekazujemy wyniki, stan prac nad QA oraz pozostałą część T024/T025.
Approval z niezależnym podpisem, pełna weryfikacja raportu i partia 0..3
pozostają dalszą pracą.
