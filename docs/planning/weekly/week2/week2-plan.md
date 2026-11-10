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

## Wejście: co zostało z tygodnia 1 (poniedziałek, przed nowymi zadaniami)

Zrobione 2026-11-02: PR-y Kuby i Roberta są w `main`; DEC-02 (profil e-bike) i DEC-11 zamknięte
w [decisions.md](../../decisions.md); wektor podpisu dostosowany do profilu e-bike etapu 2;
poprawki Flyway i adresu w YAML są w `main`; `stationCertificateId` walidowany jako bytes32.
CI nie jest rozwijane (decyzja 2026-11-02).

| Zaległość | Odpowiada | Wynik |
|---|---|---|
| Uruchomić testy `*IT` backendu z `TEST_DB_URL` (PostgreSQL z Compose) po zmianie wektora | 2 | `./mvnw verify` zielone lokalnie |
| Smoke deploy na Amoy z prawdziwą transakcją | 1 | `shared/deployments/amoy.json`, tx `completeStage` w eksploratorze |
| Badanie Pinaty (token Mikołaja) | 3 + 2 | notatka z limitami |

## Cel tygodnia

1. **Profile pomiarów e-bike egzekwowane w backendzie** (`422` dla raportu niezgodnego
   z profilem i regułą `qaPassed`, nie ukryty PASS).
2. **Auth kompletny:** SIWE -> token, role (OPERATOR, QA_INSPECTOR, administrator), owner,
   wylogowanie; `POST /batches` z numerem `B-RRRR-NNNN` i `Idempotency-Key`.
3. **Certyfikat stacji** wystawiany (Python), weryfikowany (Java, start) i zgodny z rejestrem on-chain.
4. **Backend zgodny z ABI:** test, że kodowanie `completeStage` w Javie daje bajty identyczne
   z Solidity; Web3j nadal bez `.send()`.
5. **Symulator:** pierwszy scenariusz e-bike uruchomiony na lokalnym łańcuchu i backendzie
   (jedna partia, etap 0 end-to-end, etap 1 do `202`), uruchamiany lokalnie skryptem.

## Przygotowanie przed poniedziałkiem (ok. 30 min)

| Osoba | Co |
|---|---|
| 1 | `.env` Amoy poza Git, POL z faucetu na konto testowe, `git submodule update --init` |
| 2 | Token Pinata (poza repozytorium); `./mvnw verify` na PostgreSQL z Compose |
| 3 | Docker działa; Node niepotrzebny do poniedziałku tygodnia 3 |
| 4 | venv Python 3.11+ (testy przechodzą na 3.12-3.14); Foundry (`forge`/`anvil`) do testów lokalnych |

## Osoba 1 - kontrakty, certyfikaty w Javie

| Dzień | Zadanie | Wynik |
|---|---|---|
| Pon | Zaległość: **prawdziwy smoke Amoy** (`DeploySupplyChain`, `RegisterStation`, `SmokeStage`, `export-deployment.sh 80002 amoy`), uzupełnienie notatki o faucet i tx | `amoy.json`, adres i tx w [adam-amoy-notes.md](../week1/adam-amoy-notes.md) |
| Wt | Test zgodności ABI: dane wejściowe `completeStage` w fixture, `cast calldata` vs `CompleteStageCallEncoder`; ewentualne poprawki ogłoszone osobie 2 | identyczne bajty w Javie i Solidity |
| Śr | **T010 (start):** weryfikator certyfikatu stacji w Javie (EIP-712 wystawcy `StationCertificate`, zgodność z `getStation`) na fixture `station-certificate.json` | testy: poprawny, odwołany, wygasły, adres niezgodny z rejestrem |
| Czw | **T010 (dokończenie)** i przegląd z osobą 2; skrypt deploy kontraktu dla Compose (`DeploySupplyChain` na Anvil z kontenera, jedna komenda) | certyfikat zweryfikowany, deploy lokalny jednym poleceniem |
| Pt | Testy dodatkowe kontraktu: invariant (partia nie przesuwa się bez poprawnych podpisów), `forge test --gas-report`; spike indexera: okna `eth_getLogs` ≤ 10 000 bloków, projekt checkpointów | raport gazu, notatka o indexerze |

Kryterium: certyfikat weryfikowany w Javie; Amoy ma realne wdrożenie; ABI zgodne z encoderem.
Ryzyko: brak POL na Amoy -> zadanie zostaje otwarte i nie blokuje reszty.

## Osoba 2 - backend: auth, partie, walidacja profilu

| Dzień | Zadanie | Wynik |
|---|---|---|
| Pon | Poprawki Flyway/YAML (już w `main`) potwierdzone: `./mvnw verify` z `TEST_DB_URL`; konfiguracja `supplychain.relayer.min-tip-gwei` (30 dla Polygon/Amoy) | zielone testy, tip w konfiguracji |
| Wt | **T011:** role i owner (`OPERATOR_ROLE`, `QA_INSPECTOR_ROLE`, administrator), `DELETE /auth/sessions/current`, TTL 8 h / 30 min bezczynności (DEC-11), domyślna odmowa wg [access-control](../../access-control.md) | testy: brak roli `403`, wygasły token `401`, wylogowanie |
| Śr | `POST /api/v1/batches` z `Idempotency-Key`, generator `B-RRRR-NNNN`, `batchId = keccak256`, `GET /batches/{id}` i `/batch-numbers/{n}` (z bazy, bez indexera) | duplikat klucza zwraca ten sam `commandId` |
| Czw | **Walidator profilu pomiarów (część T015):** profile e-bike jako dane (JSON/YAML), nieznane klucze i wartości spoza zakresu -> `422`, `qaPassed` zgodne z oceną | testy mutacji: każdy klucz i granica |
| Pt | **T014:** migracje `attempts`, `qa_approvals` zgodne z [data-model](../../data-model.md); `GET /commands/{id}` z autoryzacją właściciela; spike Pinata (z osobą 3) | migracje, uprawnienia statusu |

Kryterium: `POST /batches` -> `202`, `POST /stages` z profilem e-bike -> `202`, raport poza profilem -> `422`.
Ryzyko: profile mogą się jeszcze zmienić po przeglądzie -> zmiana wymaga ogłoszenia i nowego wektora (osoba 4).

## Osoba 3 - schematy, profile, magazyn raportów (zamiast frontendu)

| Dzień | Zadanie | Wynik |
|---|---|---|
| Pon | Profile e-bike v1 w `telemetry.schema.json` są zatwierdzone (DEC-02); uzupełnić zestaw przykładów poprawnych i błędnych (po jednym na każdą regułę `qaPassed` z [product-scenario](../../product-scenario.md)) | przykłady w `shared/fixtures/` |
| Wt | **T002:** poprawki OpenAPI po recenzji osoby 2; walidacja OpenAPI 3.1; test zgodności odpowiedzi backendu ze schematem (`POST /stages`, `POST /batches`) uruchamiany lokalnie (`./mvnw test`) | OpenAPI zatwierdzone, test zgodności przechodzi |
| Śr | **T016 (część):** adapter `ReportStorage` dla Kubo (upload bajtów JCS jako plik, `cid-version`, walidacja CIDv0/v1, pobranie i porównanie hashu); przegląd osoby 2 | test integracyjny na Kubo z Compose |
| Czw | Spike Pinaty z tokenem osoby 2: wersja CID, limity planu, pobranie przez bramkę (zaległość z tygodnia 1) | notatka o Pinacie |
| Pt | Compose: jedna komenda stawia PostgreSQL, Anvil z wdrożonym kontraktem (skrypt osoby 1) i Kubo; opis resetu w README | `docker compose up` + deploy lokalny |

Kryterium: OpenAPI i profile zatwierdzone; Kubo zapisuje i odczytuje raport; środowisko z kontraktem startuje jedną komendą.
Ryzyko: Java nie jest językiem osoby 3 -> przegląd osoby 2 przed merge `ReportStorage`; para w środę.

## Osoba 4 - symulator, certyfikaty, scenariusz e-bike

| Dzień | Zadanie | Wynik |
|---|---|---|
| Pon | Wektor jest już dostosowany do profilu e-bike (2026-11-02); dopisać wektor negatywny (np. raport etapu 2 z `defectsFound > 0` i `qaPassed=true`) do testów Pythona i fixture'ów | wektor negatywny dla osób 1, 2 |
| Wt | **T009:** modele raportu zgodne z profilem, deterministyczne generatory pomiarów e-bike (wartość nominalna, tolerancje, wyjście poza tolerancję = `qaPassed=false`) | ten sam seed -> ten sam raport |
| Śr | Certyfikat stacji: końcowy `station-certificate.schema.json`, CLI wystawcy, przykład w `shared/fixtures/`, wektor certyfikatu dla osoby 1 (T010) | fixture certyfikatu, test w Pythonie |
| Czw | Scenariusze YAML: `ebike-happy-path` (etapy 0-1), `ebike-qa-fail` (etap 1 poza tolerancją); runner wołający lokalny backend, odczyt `getBatch` przez RPC (DEC-10) | scenariusze opisane i uruchomione |
| Pt | Skrypt lokalnego przebiegu (anvil + backend + symulator, uruchamiany ręcznie): partia, etap 0, `202` dla etapu 1; **demo tygodnia** | pierwszy przepływ end-to-end do `202` |

Kryterium: symulator wystawia certyfikat, podpisuje raport e-bike i otrzymuje `202` od lokalnego backendu.
Ryzyko: zmiana profilu blokuje generatory i walidator; wymaga ogłoszenia i nowego wektora, ma pierwszeństwo przed scenariuszami.

## Piątek - demo i odbiór tygodnia (wszyscy, 60 min)

Kolejność: smoke Amoy z eksploratora -> `docker compose up` z kontraktem ->
symulator e-bike: partia i etapy do `202` -> `422` dla raportu spoza profilu -> certyfikat
zweryfikowany w Javie -> przegląd OpenAPI. Następnie retrospektywa i plan tygodnia 3
(start frontendu).

## Definicja ukończenia tygodnia

- [ ] Testy `*IT` backendu przechodzą lokalnie z `TEST_DB_URL` (po zmianie wektora z 2026-11-02).
- [ ] Profile pomiarów e-bike egzekwowane przez walidator backendu (`422`, reguły `qaPassed`), zgodne z wektorem.
- [ ] Smoke Amoy wykonany, adres i hash transakcji w `shared/deployments/amoy.json`.
- [ ] Auth z rolami i ownerem; `POST /batches` z `Idempotency-Key`; `GET /commands/{id}` tylko dla uprawnionych.
- [ ] Kodowanie `completeStage` w Javie zgodne bajt w bajt z kontraktem.
- [ ] Weryfikator certyfikatu przechodzi testy zgodności z rejestrem.
- [ ] Adapter Kubo `ReportStorage` działa; notatka o Pinacie istnieje.
- [ ] Symulator: scenariusz e-bike do `202` na lokalnym środowisku.
- [ ] OpenAPI zatwierdzone, test zgodności odpowiedzi przechodzi lokalnie.
- [ ] Żadnych sekretów w repozytorium (`.env`, tokeny Pinaty, klucze Amoy).

## Zależności i praca równoległa w tygodniu 2

Zależność dotyczy konkretnego artefaktu lub integracji, nie całego dnia pracy. Osoba
czekająca na kontrakt, fixture albo środowisko może równolegle przygotować szkielet,
testy jednostkowe i niezależną część zadania; nie powinna jednak oznaczać integracji
ani kryterium odbioru jako ukończonego przed otrzymaniem wejścia.

| Osoba | Co może robić równolegle | Na co czeka / co blokuje odbiór |
|---|---|---|
| **1** | Poniedziałkowy smoke Amoy jest niezależny od prac pozostałych osób. We wtorek test ABI można prowadzić równolegle z T011 osoby 2 i T009 osoby 4. Skrypt deploy na Anvil można pisać w czwartek równolegle z walidatorem profilu. | Test ABI korzysta ze wspólnego fixture/wektora; różnicę w encoderze przekazuje osobie 2 od razu, ale nie blokuje jej auth. T010 można zacząć od szkieletu w środę, natomiast testy końcowe wymagają certyfikatu i fixture osoby 4. Recenzja T010 należy do osoby 2. Integracja skryptu deploy z Compose czeka na konfigurację osoby 3. |
| **2** | Potwierdzenie testów backendu i T011 są niezależne od smoke Amoy, adaptera Kubo i generatorów osoby 4. Endpointy partii można rozwijać w środę równolegle z adapterem `ReportStorage`; walidator profilu można pisać równolegle z przygotowaniem scenariuszy osoby 4. | T002/OpenAPI wymaga przekazania uwag do osoby 3 na początku tygodnia, a końcowa zgodność DTO — uzgodnienia schematu. Walidator musi użyć zatwierdzonego profilu i wektorów/negatywnych przykładów osoby 4. Spike Pinaty w piątek wykonuje z osobą 3; osoba 3 potrzebuje tokenu przekazanego przez osobę 2 poza repozytorium. |
| **3** | Przykłady schematów i OpenAPI można przygotować równolegle z T011/T009. Adapter Kubo można rozwijać równolegle z endpointami partii, używając istniejącego lokalnego Kubo. Compose można konfigurować równolegle z pisaniem skryptu deploy przez osobę 1. | Poprawki OpenAPI czekają na konkretne uwagi osoby 2; przed odbiorem muszą odpowiadać rzeczywistym DTO i odpowiedziom backendu. Adapter `ReportStorage` wymaga przeglądu osoby 2. Spike Pinaty wymaga tokenu osoby 2. Pełny start Compose z kontraktem w piątek wymaga skryptu Anvil osoby 1. |
| **4** | Wektor negatywny i generatory T009 można rozwijać niezależnie od backendu, ABI i Kubo, przy niezmienionym profilu e-bike. Scenariusze YAML i runner można przygotować, gdy osoba 2 implementuje walidator. | Wektor negatywny z poniedziałku jest wejściem dla testów osób 1 i 2. T010 osoby 1 czeka na schemat/fixture certyfikatu ze środy. Wykonanie `ebike-qa-fail` wymaga działającego endpointu i walidatora osoby 2. Pełny przebieg E2E w piątek wymaga lokalnego środowiska osoby 3 oraz deployu osoby 1. |

### Przekazania, które wyznaczają kolejność

| Kiedy | Przekazanie | Co może ruszyć wcześniej, a co czeka |
|---|---|---|
| **Poniedziałek** | Osoba 4 publikuje negatywny wektor i przykłady w `shared/fixtures/` dla osób 1 i 2. Osoba 3 publikuje przykłady profilu. | Osoby 1 i 2 mogą pracować nad szkieletem testów wcześniej; testy zgodności profilu kończą po otrzymaniu wspólnego wektora. Smoke Amoy i testy backendu nie czekają na te fixture'y. |
| **Wtorek** | Osoba 2 przekazuje osobie 3 uwagi do OpenAPI; osoba 1 publikuje wynik porównania ABI i zgłasza ewentualną różnicę osobie 2. | Osoba 3 może równolegle walidować pozostałe schematy; poprawki DTO/encoderów zależne od uwag zaczynają się po ich przekazaniu. Auth i T009 nie czekają na wynik ABI. |
| **Środa** | Osoba 4 przekazuje osobie 1 zatwierdzony `station-certificate.schema.json` i `station-certificate.json`; osoba 2 przegląda adapter osoby 3. | Osoba 1 może wcześniej przygotować interfejs i testy weryfikatora, ale testy poprawnego/odwołanego/wygasłego certyfikatu wymagają fixture'u. Adapter Kubo może być implementowany bez czekania na endpointy partii, lecz przed scaleniem wymaga przeglądu. |
| **Czwartek** | Osoba 2 udostępnia działający walidator profilu i kontrakt endpointu; osoba 3 przekazuje osobie 2 wyniki spike'u Pinaty. | Osoba 4 może pisać runner i YAML wcześniej, ale wykonanie negatywnego scenariusza czeka na walidator/backend. Spike Pinaty czeka na token od osoby 2, nie blokuje adaptera Kubo dla Compose. |
| **Piątek** | Osoba 1 przekazuje skrypt deploy Anvil, a osoba 3 uruchamialny Compose z PostgreSQL, Anvil i Kubo; osoba 2 udostępnia endpointy i walidację; osoba 4 spina symulator. | Demo E2E jest wspólną bramką: kolejno potrzebne są działające usługi, wdrożony kontrakt, backend z walidacją i runner. Brak POL blokuje wyłącznie smoke Amoy, nie lokalny przebieg. |

**Równolegle przez większość tygodnia:** prace on-chain/Amoy osoby 1, auth i partie osoby
2, schematy/Kubo osoby 3 oraz modele i fixture'y osoby 4. Wspólnymi punktami synchronizacji
są formaty OpenAPI/DTO, wektory i fixture'y, certyfikat oraz piątkowa integracja. Zmiana
profilu lub podpisanych pól w trakcie tygodnia wstrzymuje zależne testy osób 1, 2 i 4 do
ogłoszenia zmiany i opublikowania nowego wektora.

## Czego w tym tygodniu NIE robimy

- Rozwijania CI (decyzja 2026-11-02); istniejący szkielet w `.github/` zostaje bez zmian.
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
