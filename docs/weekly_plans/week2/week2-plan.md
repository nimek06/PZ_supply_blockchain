# Plan drugiego tygodnia implementacji (bez frontendu)

**Status:** plan roboczy dla 4 osób, tydzień 2 z 12. Kontynuacja
[week1-plan.md](../week1/week1-plan.md); stan tygodnia 1: [week1_summary.md](../week1/week1_summary.md).
Zadania z [team-split](../../team-split.md), zakres i kolejność z [plan.md](../../plan.md).
Scenariusz produktu: [product-scenario.md](../../product-scenario.md) (e-bike, system ogólny).

**Frontend nie jest realizowany także w tygodniu 2** (decyzja 2026-11-02). T005 (inicjalizacja),
T032 (HTTPS, skaner QR) i wektor w TypeScripcie (część T008) przechodzą na **poniedziałek
tygodnia 3**. Osoba 3 nadal wspiera backend, schematy i infrastrukturę, dzięki czemu frontend
ruszy z zatwierdzonym OpenAPI, profilami e-bike i działającym środowiskiem lokalnym.
Pozycje to zadania do wykonania, nie raport ukończenia.

## Wejście: co musi być domknięte z tygodnia 1 (poniedziałek, przed nowymi zadaniami)

| Zaległość | Odpowiada | Wynik |
|---|---|---|
| PR-y gałęzi `kuba/week1-schemas-compose` i `robert/week1-simulator-vector` do `main` | 3, 4 (recenzja: 2, 1) | w `main` są OpenAPI, schematy, Compose, symulator, wektor w `shared/fixtures/`, szkielet CI |
| Wpis w `docs/decisions.md` z datą i inicjałami: potwierdzenie v2 (DEC-01, 03-08, 10, 12-16), DEC-02, DEC-11, zmiana DEC-01 na scenariusz e-bike | wszyscy | decyzje zamknięte lub jawny sprzeciw |
| Niezgodność wektora z profilami etapu 2 (wykryta przez osobę 3) | 3 + 4 | jeden zestaw kluczy; jeśli zmiana, nowy wektor osoby 4 i ogłoszenie |
| Poprawki Flyway i adresu w YAML testów (znalezione przez osobę 3) | 2 | zatwierdzone w `main`, `./mvnw verify` zielone |
| Smoke deploy na Amoy z prawdziwą transakcją | 1 | `shared/deployments/amoy.json`, tx `completeStage` w eksploratorze |

## Cel tygodnia

1. **Profile pomiarów e-bike zamrożone** (DEC-02) i egzekwowane w backendzie (`422` dla raportu
   niezgodnego z profilem, nie ukryty PASS).
2. **Auth kompletny:** SIWE -> token, role (OPERATOR, QA_INSPECTOR, administrator), owner,
   wylogowanie; `POST /batches` z numerem `B-RRRR-NNNN` i `Idempotency-Key`.
3. **Certyfikat stacji** wystawiany (Python), weryfikowany (Java, start) i zgodny z rejestrem on-chain.
4. **Backend zgodny z ABI:** test, że kodowanie `completeStage` w Javie daje bajty identyczne
   z Solidity; Web3j nadal bez `.send()`.
5. **CI w repozytorium:** `forge`, `pytest` i `mvnw verify` uruchamiane na każdym PR.
6. **Symulator:** pierwszy scenariusz e-bike uruchomiony na lokalnym łańcuchu i backendzie
   (jedna partia, etap 0 end-to-end, etap 1 do `202`).

## Przygotowanie przed poniedziałkiem (ok. 30 min)

| Osoba | Co |
|---|---|
| 1 | `.env` Amoy poza Git, POL z faucetu na konto testowe, `git submodule update --init` |
| 2 | Token Pinata (poza repozytorium); `./mvnw verify` na PostgreSQL z Compose |
| 3 | Docker działa; Node niepotrzebny do poniedziałku tygodnia 3 |
| 4 | venv Python 3.12; Foundry (`forge`/`anvil`) do testów integracyjnych w CI |

## Osoba 1 - kontrakty, CI, certyfikaty w Javie

| Dzień | Zadanie | Wynik |
|---|---|---|
| Pon | Zaległość: **prawdziwy smoke Amoy** (`DeploySupplyChain`, `RegisterStation`, `SmokeStage`, `export-deployment.sh 80002 amoy`), uzupełnienie notatki o faucet i tx | `amoy.json`, adres i tx w [adam-amoy-notes.md](../week1/adam-amoy-notes.md) |
| Wt | **T028:** CI w `.github/workflows/` na bazie szkieletu osoby 4: joby contracts (submoduły, `forge fmt --check`, `forge test`), backend, simulator; cache | zielony workflow na PR |
| Śr | Test zgodności ABI: dane wejściowe `completeStage` w fixture, `cast calldata` vs `CompleteStageCallEncoder`; ewentualne poprawki ogłoszone osobie 2 | identyczne bajty w Javie i Solidity |
| Czw | **T010 (start):** weryfikator certyfikatu stacji w Javie (EIP-712 wystawcy `StationCertificate`, zgodność z `getStation`); przegląd osoby 2 | testy: poprawny, odwołany, wygasły, adres niezgodny z rejestrem |
| Pt | Testy dodatkowe kontraktu: invariant (partia nie przesuwa się bez poprawnych podpisów), `forge test --gas-report`; spike indexera: okna `eth_getLogs` ≤ 10 000 bloków, projekt checkpointów | raport gazu, notatka o indexerze |

Kryterium: CI zielone; certyfikat weryfikowany w Javie; Amoy ma realne wdrożenie.
Ryzyko: brak POL na Amoy -> zadanie zostaje otwarte i nie blokuje reszty.

## Osoba 2 - backend: auth, partie, walidacja profilu

| Dzień | Zadanie | Wynik |
|---|---|---|
| Pon | Poprawki Flyway/YAML od osoby 3, `./mvnw verify`; konfiguracja `supplychain.relayer.min-tip-gwei` (30 dla Polygon/Amoy) | zielone testy, tip w konfiguracji |
| Wt | **T011:** role i owner (`OPERATOR_ROLE`, `QA_INSPECTOR_ROLE`, administrator), `DELETE /auth/sessions/current`, TTL 8 h / 30 min bezczynności (DEC-11), domyślna odmowa wg [access-control](../../access-control.md) | testy: brak roli `403`, wygasły token `401`, wylogowanie |
| Śr | `POST /api/v1/batches` z `Idempotency-Key`, generator `B-RRRR-NNNN`, `batchId = keccak256`, `GET /batches/{id}` i `/batch-numbers/{n}` (z bazy, bez indexera) | duplikat klucza zwraca ten sam `commandId` |
| Czw | **Walidator profilu pomiarów (część T015):** profile e-bike jako dane (JSON/YAML), nieznane klucze i wartości spoza zakresu -> `422`, `qaPassed` zgodne z oceną | testy mutacji: każdy klucz i granica |
| Pt | **T014:** migracje `attempts`, `qa_approvals` zgodne z [data-model](../../data-model.md); `GET /commands/{id}` z autoryzacją właściciela; spike Pinata (z osobą 3) | migracje, uprawnienia statusu |

Kryterium: `POST /batches` -> `202`, `POST /stages` z profilem e-bike -> `202`, raport poza profilem -> `422`.
Ryzyko: profile niezatwierdzone -> walidator działa na profilu roboczym za flagą do czasu DEC-02.

## Osoba 3 - schematy, profile, magazyn raportów (zamiast frontendu)

| Dzień | Zadanie | Wynik |
|---|---|---|
| Pon | PR gałęzi do `main`; po DEC-02 **ostateczne profile e-bike** w `telemetry.schema.json` ([product-scenario](../../product-scenario.md)); zestaw przykładów poprawnych i błędnych | profile zatwierdzone przez zespół |
| Wt | **T002:** poprawki OpenAPI po recenzji osoby 2; walidacja OpenAPI 3.1; test zgodności odpowiedzi backendu ze schematem (`POST /stages`, `POST /batches`) | OpenAPI zatwierdzone, test w CI |
| Śr | **T016 (część):** adapter `ReportStorage` dla Kubo (upload bajtów JCS jako plik, `cid-version`, walidacja CIDv0/v1, pobranie i porównanie hashu); przegląd osoby 2 | test integracyjny na Kubo z Compose |
| Czw | Spike Pinaty z tokenem osoby 2: wersja CID, limity planu, pobranie przez bramkę (zaległość z tygodnia 1) | notatka o Pinacie |
| Pt | Compose: jedna komenda stawia PostgreSQL, Anvil z wdrożonym kontraktem (skrypt osoby 1) i Kubo; opis resetu w README | `docker compose up` + deploy lokalny |

Kryterium: OpenAPI i profile zatwierdzone; Kubo zapisuje i odczytuje raport; środowisko z kontraktem startuje jedną komendą.
Ryzyko: Java nie jest językiem osoby 3 -> przegląd osoby 2 przed merge `ReportStorage`; para w środę.

## Osoba 4 - symulator, certyfikaty, scenariusz e-bike

| Dzień | Zadanie | Wynik |
|---|---|---|
| Pon | PR gałęzi do `main`; jeśli DEC-02 zmienia klucze etapu 2: **nowy `signature-vector.json`** (ogłoszenie na kanale), testy Pythona | wektor zgodny z profilem; osoby 1, 2 aktualizują testy |
| Wt | **T009:** modele raportu zgodne z profilem, deterministyczne generatory pomiarów e-bike (wartość nominalna, tolerancje, wyjście poza tolerancję = `qaPassed=false`) | ten sam seed -> ten sam raport |
| Śr | Certyfikat stacji: końcowy `station-certificate.schema.json`, CLI wystawcy, przykład w `shared/fixtures/`, wektor certyfikatu dla osoby 1 (T010) | fixture certyfikatu, test w Pythonie |
| Czw | Scenariusze YAML: `ebike-happy-path` (etapy 0-1), `ebike-qa-fail` (etap 1 poza tolerancją); runner wołający lokalny backend, odczyt `getBatch` przez RPC (DEC-10) | scenariusze opisane i uruchomione |
| Pt | Test integracyjny w CI (anvil + backend + symulator): partia, etap 0, `202` dla etapu 1; **demo tygodnia** | pierwszy przepływ end-to-end do `202` |

Kryterium: symulator wystawia certyfikat, podpisuje raport e-bike i otrzymuje `202` od lokalnego backendu.
Ryzyko: spóźniony DEC-02 blokuje generatory i walidator; ma pierwszeństwo przed scenariuszami.

## Piątek - demo i odbiór tygodnia (wszyscy, 60 min)

Kolejność: smoke Amoy z eksploratora -> CI zielone na PR -> `docker compose up` z kontraktem ->
symulator e-bike: partia i etapy do `202` -> `422` dla raportu spoza profilu -> certyfikat
zweryfikowany w Javie -> zatwierdzenie OpenAPI i profili. Następnie retrospektywa i plan tygodnia 3
(start frontendu).

## Definicja ukończenia tygodnia

- [ ] PR-y z tygodnia 1 w `main`; `decisions.md` z datami i inicjałami, DEC-02 i DEC-11 zamknięte.
- [ ] Profile pomiarów e-bike zamrożone, zgodne z wektorem i z walidatorem backendu.
- [ ] Smoke Amoy wykonany, adres i hash transakcji w `shared/deployments/amoy.json`.
- [ ] CI uruchamia testy contracts, backend i simulator na każdym PR i jest zielone.
- [ ] Auth z rolami i ownerem; `POST /batches` z `Idempotency-Key`; `GET /commands/{id}` tylko dla uprawnionych.
- [ ] Kodowanie `completeStage` w Javie zgodne bajt w bajt z kontraktem.
- [ ] Weryfikator certyfikatu (start) przechodzi testy zgodności z rejestrem.
- [ ] Adapter Kubo `ReportStorage` działa; notatka o Pinacie istnieje.
- [ ] Symulator: scenariusz e-bike do `202` na lokalnym środowisku.
- [ ] OpenAPI zatwierdzone, test zgodności odpowiedzi w CI.
- [ ] Żadnych sekretów w repozytorium (`.env`, tokeny Pinaty, klucze Amoy).

## Zależności między osobami w tygodniu 2

```text
Pon: osoba 3 domyka profile (DEC-02) -> osoba 4 generuje wektor i generatory, osoba 2 walidator
Wt:  osoba 1 oddaje CI -> wszyscy mają zielone PR; osoba 3 oddaje OpenAPI -> osoba 2 poprawia DTO
Śr:  osoba 1 oddaje test ABI -> osoba 2 poprawia encoder przy różnicy; osoba 4 oddaje certyfikat -> osoba 1 (T010)
Czw: osoba 2 oddaje walidator profilu -> osoba 4 uruchamia scenariusz qa-fail
Pt:  osoba 3 oddaje Compose z kontraktem -> osoba 4 test e2e w CI
```

## Czego w tym tygodniu NIE robimy

- Frontend, HTTPS, QR i wektor w TypeScripcie (tydzień 3).
- Worker, relayer wysyłający transakcje i indexer (tydzień 4-5; teraz tylko spike indexera).
- API kolejki QA i `POST` approval (tydzień 5-6).
- Zmian podpisanych pól bez ogłoszenia; identyfikator profilu/produktu w raporcie v2
  (patrz [product-scenario](../../product-scenario.md) pkt 4.3).

## Skutki dla tygodnia 3

- T005 i T032 (inicjalizacja frontendu, HTTPS + QR) oraz wektor TS startują w poniedziałek tygodnia 3,
  na zatwierdzonym OpenAPI, profilach e-bike i gotowym środowisku lokalnym.
- Osoba 1 kończy T010 i przygotowuje T013 (deploy ról) oraz start indexera (T019).
- Osoba 2 wchodzi w T015 (reguła zastępowania w pełni, Problem Details) i T017 (nadawca transakcji).
