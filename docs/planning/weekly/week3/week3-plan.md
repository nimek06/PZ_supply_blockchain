# Plan tygodnia 3 — domknięcie fundamentów i start frontendu

**Data planu:** 2026-11-11. **Status:** do wykonania, nie raport ukończenia.
Tydzień oznacza etap harmonogramu projektu, nie datę kalendarzową.
Podstawa: [wyniki tygodnia 2](../week2/week2-summary.md),
[plan 12 tygodni](../../plan.md), [podział zadań](../../team-split.md)
i [wariant 1 / DEC-17](../../../decisions/local-blockchain-scope.md).
Kontynuacja: [tydzień 4](../week4/week4-plan.md).

## Plany osobiste

- [Adam — kontrakty i łańcuch](plans/adam-plan.md)
- [Mikołaj — backend i trwała kolejka](plans/mikolaj-plan.md)
- [Kuba — frontend](plans/kuba-plan.md)
- [Robert — symulator i integracja](plans/robert-plan.md)

Plany osobiste rozwijają ten sam podział W3-01..W3-12, nie dodają nowych zadań.
W razie zmiany zakresu należy zaktualizować plan wspólny i właściwy plan osoby.

## 1. Cel i granice

Na koniec tygodnia mamy odtwarzalne środowisko Anvil/Kubo/PostgreSQL,
bezpieczne trwałe przyjęcie polecenia przez backend i pierwszy działający frontend
z logowaniem portfelem oraz widokami na uzgodnionych danych.

**Nie obiecujemy jeszcze wykonania polecenia on-chain przez backend.** `202`
oznacza przyjęcie, nie ukończenie. Wysyłka, worker i pierwsze projekcje to tydzień 4;
pełny pion i kolejka QA rozwijają się w tygodniu 5, panel approval w tygodniu 6.
Nie robimy Pinaty, wdrożenia aplikacji na Amoy ani nowego mechanizmu kotwiczenia.
Nie rozszerzamy CI o nowe niezwiązane zadania; korzystamy z istniejących kontroli.

### Co już jest — nie piszemy ponownie

| Obszar | Stan wejściowy z tygodnia 2 | Teraz |
|---|---|---|
| Kontrakt i kryptografia | kontrakt, ABI parity, invariant/gas, weryfikator certyfikatu Java i odczyt `getStation` | sprawdzić konfigurację i wpiąć w rzeczywistą ścieżkę przyjęcia |
| Backend | SIWE/sesje/role, partie, profile, polecenia, migracje V1–V5 | domknąć luki integracyjne, lease i regułę zastępowania |
| Storage | `KuboReportStorage`, CID i testy | sprawdzić uruchomienie lokalne, nie tworzyć drugiego adaptera |
| Symulator | generatory, certyfikaty, klient, runner, YAML i dry-run/accept-only | dopasować do rzeczywistego wdrożenia i statusów |
| Frontend | katalogi z README, bez manifestu aplikacji | uruchomić T005/T032 i zaległy wektor TypeScript |

Wymagania i zasady: [spec](../../../project/spec.md),
[architektura](../../../architecture/architecture.md),
[protokół](../../../architecture/protocol.md), [model danych](../../../architecture/data-model.md).
Zachowujemy dwa klucze, certyfikaty, JCS/Keccak/EIP-712, jeden relayer
i PostgreSQL jako kolejkę. Brak osobnej konstytucji i grup topologicznych;
wytyczne migracji frameworków nie dotyczą tej kontynuacji projektu.

## 2. Kroki i zadania

Ścieżki są względem katalogu repozytorium. Txxx to zadanie z planu głównego;
W3-xx identyfikuje konkretny przyrost, nie nowe zadanie zamiast Txxx.
`[P]` oznacza możliwość pracy równoległej po uzgodnieniu interfejsów.

### P3.1 — Adam: lokalny deploy i tożsamość stacji

**Wymagania:** REQ-001, REQ-004, REQ-005, REQ-008, REQ-013, REQ-014.
**Przegląd:** Mikołaj dla Javy, Robert dla uruchomienia.

- [ ] W3-01 [P] [Plan:3.1] T013: sprawdzić istniejące `contracts/script/deploy-local.sh`, `DeploySupplyChain.s.sol`, `RegisterStation.s.sol`, `shared/abi/SupplyChain.abi.json` i `shared/deployments/local.json`; domknąć konfigurację osobnych adresów administratora, relayera, stacji i QA oraz rejestracji stacji etapów 0..3. Ponowne uruchomienie nie może niejawnie resetować łańcucha; opisać świadomy reset.
- [ ] W3-02 [Plan:3.1] T010: domknąć integrację istniejących `Eip712StationCertificateVerifier.java`, `StationCertificatePolicy.java`, `Web3jStationRegistry.java` w `backend/src/main/java/pl/pz/supplyblockchain/infrastructure/blockchain/` i portu `StationCertificateVerifier` z konfiguracją rzeczywistego lokalnego wdrożenia. Wynik domenowy to `StationIdentityVerification`. Przekazać Mikołajowi interfejs walidacji przed zapisem polecenia; nie przepisywać weryfikatora.
- [ ] W3-03 [P] [Plan:3.1] T019 (projekt): uaktualnić `docs/planning/weekly/week2/notes/adam-indexer-notes.md` przez dodanie bieżących ustaleń albo zapisać ich kontynuację w `docs/planning/weekly/week3/notes/`; uzgodnić z Mikołajem checkpoint, identyfikator deploymentu, event → command i granicę `CONFIRMED`. Przygotować zakres migracji projekcji i parametry `latest`/1 potwierdzenie/okno ponownego skanu, bez projektu indexera Amoy.

### P3.2 — Mikołaj: trwałe przyjęcie i przygotowanie kolejki

**Wymagania:** REQ-002, REQ-004, REQ-005, REQ-006, REQ-009, REQ-012, REQ-013.
**Przegląd:** Adam dla łańcucha, Robert dla przebiegu API.

- [ ] W3-04 [Plan:3.2] T010/T011/T015: wpiąć wynik W3-02 do `backend/src/main/java/pl/pz/supplyblockchain/application/SubmitStageService.java` i konfiguracji Springa; odrzucać certyfikat nieznany, wygasły, odwołany, z niezgodnym adresem lub zakresem. Sprawdzić istniejące `AuthService.java`, `RoleService.java`, `SessionContextService.java`, `CommandQueryService.java`: role, owner, logout, `401`/`403`. Niedostępne RPC nie może oznaczać pozytywnej weryfikacji.
- [ ] W3-05 [Plan:3.2] T014/T015: domknąć `SubmitStageService.java`, `CreateBatchService.java`, porty/repozytoria poleceń i constraints w `backend/src/main/java/pl/pz/supplyblockchain/infrastructure/persistence/`. Zachować idempotencję; zastąpienie po FAILED oprzeć na timestampie potwierdzonego bloku i rozstrzygnięciu prób, nie samym zegarze JVM. Nowe zmiany schematu wyłącznie kolejną migracją w `backend/src/main/resources/db/migration/`, bez zmiany V1–V5.
- [ ] W3-06 [Plan:3.2] T014/T018 (przygotowanie): ustalić i dodać przejęcie/lease oraz trwałe aktualizacje statusów przez porty i adaptery w `backend/src/main/java/pl/pz/supplyblockchain/application/ports/` i `infrastructure/persistence/`. Przekazać kontrakt workera dla tygodnia 4: jedna instancja, krótkie transakcje DB, I/O poza blokadą, odzysk wygasłego lease. Adam dostaje interfejs checkpoint/projekcji z W3-03.

### P3.3 — Kuba: pierwszy frontend lokalny

**Wymagania:** REQ-003, REQ-004, REQ-009, REQ-010, REQ-012, REQ-014.
**Przegląd:** Robert dla UI/QR, Adam dla EIP-712.

- [ ] W3-07 [P] [Plan:3.3] T005/T032: zainicjalizować React/TypeScript/Vite w istniejącym `frontend/`, zachowując README; dodać `frontend/package.json`, lockfile, router i providery w `frontend/src/app/`, lokalny HTTPS oraz komendy type-check/lint/build. Sprawdzić skaner QR na telefonie i zapisać wybór biblioteki w `frontend/README.md`.
- [ ] W3-08 [Plan:3.3] T008/T011: dodać weryfikację `shared/fixtures/signature-vector.json` w `frontend/tests/` i obsługę lokalnej sieci/portfela w `frontend/src/lib/blockchain/`; w `frontend/src/lib/api/client.ts` obsłużyć SIWE challenge/verify/logout i Bearer tylko w pamięci. Przy zmianie konta/sieci wyczyścić sesję; odmowa podpisu ma być widocznym wynikiem, nie sukcesem.
- [ ] W3-09 [Plan:3.3] T022 (start): przygotować widoki partii/statusu w `frontend/src/features/batches/` i `frontend/src/features/operator/` zgodne z `shared/schemas/openapi.yaml`. Na fixture'ach pokazać loading/empty/pending/error; podłączyć dostępne odczyty lokalne, wyraźnie oznaczać dane demonstracyjne i `stale`. Nie przedstawiać samego `202` ani `stale=true` jako potwierdzonej produkcji.

### P3.4 — Robert: scenariusze i bramka integracyjna

**Wymagania:** REQ-003, REQ-004, REQ-005, REQ-006, REQ-011, REQ-012, REQ-013.
**Przegląd:** Mikołaj dla API, Kuba dla instrukcji uruchomienia.

- [ ] W3-10 [P] [Plan:3.4] T009/T020: dopasować istniejące `simulator/src/supply_chain_simulator/cli.py`, `client.py`, `runner.py` oraz `simulator/scenarios/` do aktualnego OpenAPI; zachować dry-run, accept-only i deterministyczny seed, bez nowego runnera.
- [ ] W3-11 [Plan:3.4] T020/T027 (przygotowanie): po W3-01/W3-04/W3-05 uruchomić poprawne zgłoszenie oraz negatywne fixture'y profilu, podpisu i certyfikatu przez lokalne API. Przy braku workera scenariusz kończy się na trwałym `202`, nie czeka na fikcyjne potwierdzenie. Wynik opisać w `simulator/scenarios/README.md`.
- [ ] W3-12 [Plan:3.4] T030 (przygotowanie): przeprowadzić wspólny odbiór i zapisać faktyczne komendy, wyniki, blokady oraz przekazanie do tygodnia 4 w `docs/planning/weekly/week3/week3-summary.md` dopiero po wykonaniu. Uruchomienie i reset opisać w `docs/guides/development.md`; nie dopisywać planowanych wyników jako zaliczonych.

## 3. Podział na dni

| Dzień | Adam | Mikołaj | Kuba | Robert |
|---|---|---|---|---|
| Poniedziałek | W3-01: lokalny deploy i adresy | W3-05: przegląd trwałego przyjęcia, uzgodnienie portów | W3-07: Vite/TS/router | W3-10: scenariusze i rzeczywisty kontrakt API |
| Wtorek | W3-02: konfiguracja i kontrakt weryfikacji certyfikatu | W3-04: wpięcie certyfikatu, auth/owner | W3-08: wektor TS i sieć Anvil | W3-10: dry-run/accept-only i tożsamości |
| Środa | W3-03: checkpoint/projekcje z Mikołajem | W3-05: reguła zastępowania i konkurencja | W3-08: SIWE/Bearer/logout | W3-11: przyjęcie i scenariusze negatywne |
| Czwartek | przegląd W3-04/W3-05 i ABI | W3-06: lease i porty workera | W3-07/W3-09: HTTPS/QR, ekrany | W3-11: restart backendu i odczyt polecenia |
| Piątek | odbiór deployu i przekazanie T019 | odbiór trwałego `202` i przygotowania kolejki | prezentacja UI i wyniku QR na telefonie | W3-12: wspólny odbiór i zapis wyników |

## 4. Zależności i przekazania

| Zadanie | Bezpośrednie zależności w tygodniu 3 |
|---|---|
| W3-01, W3-03, W3-05, W3-07, W3-10 | brak; korzystają ze stanu po tygodniu 2 |
| W3-02 | W3-01 dla sprawdzenia na rzeczywistym rejestrze |
| W3-04 | W3-02 |
| W3-06 | W3-03, W3-05 |
| W3-08, W3-09 | W3-07; testy TS można zacząć przed gotowym SIWE UI |
| W3-11 | W3-01, W3-04, W3-05, W3-10 |
| W3-12 | W3-01..W3-11; braki oznaczamy jako otwarte |

Adam i Mikołaj nie edytują równolegle `SubmitStageService.java`: Adam przekazuje
adapter/interfejs certyfikatu, Mikołaj podpina go do przyjęcia. Kuba jest autorem
zmian frontendu; Mikołaj utrzymuje OpenAPI, Robert wspólne fixture'y.
Zmienione API lub podpisane pola wymagają uzgodnienia z klientami przed merge.

## 5. Kryteria odbioru

1. Z pustego lokalnego środowiska można wdrożyć kontrakt, odczytać chainId/adres
   i rejestr stacji; ponowne uruchomienie nie resetuje danych bez zgody.
2. Poprawne przyjęcie zwraca `202` z trwałym `commandId`. **10 ponowień**
   identycznego zgłoszenia daje to samo ID, także po restarcie backendu.
3. Inna treść dla aktywnego etapu daje `409 STAGE_ALREADY_PENDING`;
   zastąpienie po FAILED respektuje potwierdzony czas bloku i wcześniejsze próby.
4. Negatywne certyfikaty/podpisy nie tworzą nowego zaakceptowanego polecenia;
   brak RPC jest jawnym błędem. Brak/wygasła sesja daje `401`, brak uprawnień `403`.
5. Frontend buduje się z type-checkiem, odtwarza wspólny wektor, loguje lokalnym
   portfelem i poprawnie obsługuje odmowę, zmianę konta/sieci i wylogowanie.
6. Telefon otwiera aplikację przez HTTPS i skanuje QR z osiągalnym adresem.
   Brak urządzenia/przeglądarki zapisujemy jako niewykonaną weryfikację.
7. Interfejsy lease, indexera i statusów są uzgodnione. Nie ma ręcznej edycji
   bazy w celu udawania `CONFIRMED`.

Każdy autor sprawdza istniejące kontrole swojego modułu zgodnie z
[testing](../../../guides/testing.md); do integracji bazy potrzebny jest PostgreSQL.
Brak Dockera nie oznacza zaliczenia pominiętych testów integracyjnych.

## 6. Requirement Mapping

Tydzień 3 realizuje przyrost, nie pełne zamknięcie wszystkich wymagań.
Pozycje odłożone mają jawne dalsze miejsce w harmonogramie.

| REQ ID | Plan Items / zadania | Dowód lub dalszy odbiór |
|---|---|---|
| REQ-001 | P3.1 / W3-01 | skrypt deployu, role i rejestr lokalny |
| REQ-002 | P3.2 / W3-05 | `CreateBatchService`, idempotencja; on-chain w tyg. 4 |
| REQ-003 | P3.3/P3.4 / W3-08, W3-10 | zgodność wektora TS, fixture'ów i modeli |
| REQ-004 | P3.1/P3.2 / W3-02, W3-04, W3-05 | certyfikat/podpis/domena i reguła zastępowania |
| REQ-005 | P3.1/P3.4 / W3-01, W3-11 | istniejący kontrakt i negatywne przyjęcie; pion QA tyg. 5–6 |
| REQ-006 | P3.2/P3.4 / W3-05, W3-06, W3-11 | trwałe polecenie i lease; wysyłka tyg. 4 |
| REQ-007 | przygotowane w tyg. 2; kontynuacja T016/P4.1 w tyg. 4 | istniejący Kubo; wpięcie workera i CID/event w tyg. 4 |
| REQ-008 | P3.1 / W3-03 | projekt checkpointu; implementacja T019 w tyg. 4–5 |
| REQ-009 | P3.2/P3.3 / W3-04, W3-09 | autoryzowany status i stany UI; panel QA tyg. 6 |
| REQ-010 | P3.3 / W3-07 | HTTPS/QR spike; pełny paszport T025 w tyg. 7 |
| REQ-011 | P3.4 / W3-10, W3-11 | runner, dry-run i accept-only |
| REQ-012 | P3.2/P3.3/P3.4 / W3-04, W3-06, W3-08, W3-11 | sesje, lease, restart, jawne błędy |
| REQ-013 | P3.1/P3.2/P3.4 / W3-02, W3-04, W3-11 | weryfikator certyfikatu w rzeczywistym przyjęciu |
| REQ-014 | P3.1/P3.3 / W3-01, W3-08 | osobny klucz QA i wektor approval; pełny pion tyg. 5–6 |

## 7. Ryzyka i przekazanie do tygodnia 4

- Brak wpięcia certyfikatu lub bezpiecznej idempotencji blokuje odbiór przyjęcia,
  ale nie równoległy start UI na oznaczonych fixture'ach.
- Nieznane wyniki prób transakcji nie mogą być zastąpione samym upływem czasu.
- Frontend zaczyna się dopiero teraz; pełny panel operatora i QA nie są celem
  jednego tygodnia. Nie przenosimy odpowiedzialności Kuby na kolejny adapter IPFS.
- Do tygodnia 4 przekazujemy: działający deploy i adresy, wejścia certyfikatów,
  porty lease/prób/projekcji, klienta SIWE, scenariusz accept-only oraz listę
  faktycznych braków. Amoy nie jest warunkiem żadnego przekazania.
