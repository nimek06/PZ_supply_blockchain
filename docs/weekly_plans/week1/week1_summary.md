# Tydzień 1 - podsumowanie stanu (2026-11-02)

Zebrane z `main`, `adam`, `kuba/week1-schemas-compose` i `robert/week1-simulator-vector`
(stan na commity: `24ae561`, `510e8e5`, `94cc32b`, `92fd48d`). Odniesienie:
[week1-plan.md](week1-plan.md) i jego "Definicja ukończenia tygodnia".

## Stan gałęzi

| Gałąź | Status |
|---|---|
| `adam` | zmergowana do `main` (PR #1) |
| `main` (Mikołaj, backend) | zmergowane bezpośrednio (PR #2) |
| `kuba/week1-schemas-compose` | **niescalona** - PR do zrobienia |
| `robert/week1-simulator-vector` | **niescalona** - PR do zrobienia |

Dopóki te dwie gałęzie nie trafią do `main`, w `main` brakuje OpenAPI, schematów,
symulatora, wektora w `shared/fixtures/` i szkieletu CI, mimo że praca jest gotowa.

## Definicja ukończenia tygodnia - punkt po punkcie

- [ ] **Decyzje v2 potwierdzone i wpisane z datą w `docs/decisions.md`.**
  Niezrobione formalnie: tylko DEC-09 ma datę `(2026-10-27)` z poprzedniej analizy.
  Reszta (DEC-01, 03-08, 10, 12-16) ma status `USTALONA` bez daty/inicjałów zespołu
  ze spotkania startowego; DEC-02 i DEC-11 nadal `DO POTWIERDZENIA`.
- [x] **Wektor `signature-vector.json` przechodzi w Pythonie, Javie i Solidity.**
  Zweryfikowane: `forge test --match-contract SignatureVectorTest` przechodzi na
  pliku Roberta (`test_SharedVector`, PASS); plik w `backend/src/test/resources/fixtures/`
  jest bajt w bajt identyczny z wektorem Roberta. Nie zmergowane do `shared/fixtures/`
  w `main` - plik istnieje tylko na gałęzi Roberta.
- [x] **`forge test`, `./mvnw verify` i `python -m pytest` działają lokalnie.**
  `forge test`: 54 passed (+1 skip do mergu wektora). `./mvnw verify` (Kuba, na Compose):
  47 unit + 3 integracyjne, zielone. `pytest` (Robert): 205 passed, 1 skipped.
- [x] **`docker compose up` podnosi PostgreSQL, Anvil i Kubo; health checki zielone.**
  Potwierdzone przez Kubę 2026-11-02 (status `healthy` po ok. 10 s), ale plik
  `compose.yaml` jest na niescalonej gałęzi.
- [x] **Kontrakt: 0 -> 3 oraz `StageRejected`; zdeployowany na Anvil i (smoke) na Amoy.**
  Lokalnie w pełni zrobione i przetestowane (`test_FullRouteZeroToThree`, 52+ testów
  negatywnych). **Smoke na Amoy NIE wykonany realnie** - tylko symulacja bez portfela
  (`evm_version=cancun` działa, tip ≈30 gwei, limity `eth_getLogs` zmierzone). Brakuje
  prawdziwej transakcji z faucetowym POL; `shared/deployments/amoy.json` nie istnieje.
- [x] **`POST /stages` zapisuje polecenie i zwraca `202`; challenge SIWE wydaje token.**
  `StageController` zwraca `202 Accepted` + `Location`; `AuthController` ma
  `/auth/challenges` i `/auth/sessions`. Reguła zastępowania (V2-02) zaimplementowana
  zgodnie z tabelą z `corrections.md`.
- [ ] **`openapi.yaml`, `telemetry.schema.json`, `eip712.json` i profile pomiarów zatwierdzone.**
  Pliki istnieją i są technicznie spójne z kontraktem, ale **nie są zatwierdzone** (DEC-02
  czeka na piątek) i mają znaną niezgodność: wektor Roberta mierzy
  `temperatureMilliC`/`torqueMilliNm`/`pressurePa` dla etapu 2, a szkic profilu w
  `telemetry.schema.json` wymaga innych kluczy (`inspectedUnits`, `defectsFound`, ...).
  Do rozstrzygnięcia: zmienić profil albo wydać nowy wektor.
- [x] **Notatki: limity Pinaty, tip gazu Amoy, limity RPC, wersje zależności.**
  Tip i limity RPC Amoy: [adam-amoy-notes.md](adam-amoy-notes.md). Kubo: zbadane,
  notatka Kuby ([kuba-ipfs-notes.md](kuba-ipfs-notes.md)). **Pinata: niezbadana**
  (czeka na token Mikołaja, celowo odłożone).
- [x] **Żadnych sekretów w repozytorium.** Nie znaleziono kluczy prywatnych ani
  tokenów w sprawdzonych commitach; `.env` wszędzie w `.gitignore`.

## Co jest zrobione (skrót po osobach)

**Adam - kontrakty i łańcuch**
`SupplyChain.sol` kompletny wg `changes-v2` (role, rejestr stacji, `completeStage`
z pełną kolejnością reguł, `StageRejected`, dwa klucze na etapie 2). 54 testy
(w tym fuzz) + test wspólnego wektora. Skrypty deployu/rejestracji/smoke na Anvil,
eksport ABI i danych wdrożenia. Smoke na Amoy - tylko przygotowanie i symulacja,
bez prawdziwej transakcji (brak portfela z POL).

**Mikołaj - backend**
Inicjalizacja Spring Boot 4.1.2 + Web3j 6.1.0. Ręczny moduł krypto EIP-712 w Javie
(bez gotowego kodera, zgodnie z V2-10), encoder `completeStage` z poprawną kolejnością
9 argumentów, podpisywanie transakcji EIP-1559 bez `.send()`/`DefaultGasProvider`.
`POST /stages` z pełną walidacją i regułą zastępowania. SIWE challenge/session (start).

**Kuba - schematy, krypto Javy (recenzja), infrastruktura** (niescalone)
`openapi.yaml`, `telemetry.schema.json`, `eip712.json`, `compose.yaml` (potwierdzone
jako działające), port `ReportStorage`, testy mutacji podpisu, badanie Kubo (IPFS).
Znalazł i zgłosił dwa realne błędy w backendzie Mikołaja (Flyway, cudzysłów adresu
kontraktu w YAML) - do przeglądu przez Mikołaja.

**Robert - symulator, integracja, dokumentacja** (niescalone)
Modele raportu, JCS, podpisy, **wspólny wektor podpisu** (opublikowany, zweryfikowany
w 3 implementacjach), klient HTTP symulatora, szkielet CI (joby contracts/simulator/backend).

## Główne braki względem planu

1. Gałęzie Kuby i Roberta nie są jeszcze w `main` - brak PR-ów.
2. Prawdziwy smoke deploy na Amoy nie wykonany (tylko symulacja offline).
3. `docs/decisions.md` nie ma dat/inicjałów z poniedziałkowego spotkania; DEC-02 i DEC-11
   formalnie otwarte.
4. Niezgodność wektora podpisu ze szkicem profili pomiarów etapu 2 (do DEC-02 w piątek).
5. Badanie Pinaty nie wykonane (celowo, brak tokenu).
