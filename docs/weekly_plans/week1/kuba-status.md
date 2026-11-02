# Tydzień 1 - co zrobił Kuba (stan na 2026-11-02)

Krótko i po ludzku. Plan zadań jest w [kuba-plan.md](kuba-plan.md), tu jest tylko to, co faktycznie powstało
i co czeka na innych.

## Zrobione

**Umowa API - `shared/schemas/openapi.yaml`**
Opis wszystkich endpointów backendu w jednym pliku: logowanie portfelem, tworzenie partii, historia partii,
wysyłanie raportu ze stacji, status polecenia i kolejka kontroli jakości z podpisem operatora. Zawiera też
format błędów i listę kodów błędów. To **szkic do przeglądu**, jeszcze nie zatwierdzony.
Backend Mikołaja (PR #2) jest z nim zgodny: te same ścieżki `/api/v1/...`, te same pola w żądaniach
i odpowiedziach (`StageSubmissionRequest`, `CommandAccepted`, `CommandStatus`, auth) i te same statusy polecenia.
Poprawki z 2026-11-02:
- koperta certyfikatu stacji ma teraz pola `certificateVersion` i `payloadHash`, tak jak
  `station-certificate.schema.json` Roberta (jego przykładowy certyfikat przechodzi);
- do listy kodów błędów doszedł `INTERNAL_ERROR`, którego używa backend.

**Format raportu - `shared/schemas/telemetry.schema.json`**
Jak ma wyglądać raport stacji. Odrzuca nieznane pola, ułamki, czas w milisekundach i adresy wielkimi literami.
Dla każdego z czterech etapów jest szkic tego, co raport mierzy (np. ile pozycji listy sprawdzeń zaliczono).
To są **wartości demonstracyjne, nie normy fizyczne** i zespół musi je zaakceptować w piątek (DEC-02).

**Typy podpisów - `shared/schemas/eip712.json`**
Dokładne definicje dwóch podpisów (stacji i operatora) w kolejności pól wymaganej przez protokół.
Sprawdzone automatycznie względem kontraktu Adama: napisy typów są identyczne.

**Moduł krypto w Javie - zrobiony razem z Mikołajem**
Kanonizację (JCS), hash Keccak-256 i ręczny EIP-712 z odzyskiem adresu napisał Mikołaj w PR #2
(`infrastructure/json/JcsReportCanonicalizer`, `infrastructure/blockchain/Web3jEip712Verifier`, `EcdsaRecovery`).
Wektor Roberta przechodzi w Javie (bajty JCS, hash, oba digesty i oba adresy).
Kuba dopisał brakujące **testy mutacji** (`SignatureMutationTest`, 25 przypadków):
- zmiana dowolnego pola raportu (także dodanie, usunięcie, zmiana nazwy lub wartości pomiaru) zmienia hash;
- kolejność pomiarów nie zmienia hasha (JCS działa);
- zmiana dowolnego pola `StageCompletion` i `StageApproval` zmienia digest i odzyskany adres;
- inny `chainId` albo inny adres kontraktu psuje podpis; podpisu stacji nie da się użyć jako zatwierdzenia;
- adres kontraktu małymi, dużymi literami lub z sumą kontrolną daje ten sam wynik, a odzyskany adres
  jest zawsze małymi literami.

**Port `ReportStorage` - szkic bez implementacji**
`application/ports/ReportStorage.java`: jedna metoda „bajty JCS -> CID”, wynik `StoredReport`,
błędy w `ReportStorageException` z powodem (timeout, brak usługi, limit, brak uprawnień, odrzucone bajty,
zła odpowiedź) i informacją, czy wolno ponowić. Adaptery Kubo i Pinaty są w tygodniu 4.

**Środowisko lokalne - `infrastructure/local/`**
`compose.yaml` stawia jedną komendą bazę PostgreSQL, lokalny łańcuch Anvil i lokalny IPFS (Kubo).
Wszystko słucha tylko na `127.0.0.1`, obrazy mają przypięte wersje, a każda usługa ma sprawdzenie zdrowia.
`.env.example` ma puste hasło do bazy - bez ustawienia go własnego start się celowo nie uda.
W README opisano, jak świadomie wyczyścić środowisko. Najważniejsze: po restarcie Anvila jego stan znika,
a baza zostaje, więc trzeba je wyczyścić razem.

**`docker compose up` - działa (2026-11-02)**
Docker 29.1 i Compose 2.40 zainstalowane z pakietów Ubuntu. Po `docker compose up -d` wszystkie trzy usługi
mają status `healthy` po ok. 10 s. Z komputera: Anvil zwraca `chainId` 31337 (`0x7a69`), Kubo zwraca wersję 0.32.1,
PostgreSQL przyjmuje połączenia. Lokalny `.env` z losowym hasłem jest tylko na dysku (w `.gitignore`).

**Badanie Kubo - zrobione**, wyniki w [kuba-ipfs-notes.md](kuba-ipfs-notes.md). W skrócie: domyślnie CIDv0,
z `cid-version=1` CIDv1 `raw`; pobrane przez bramkę bajty są identyczne, a ich Keccak równa się `reportHash`.

**Dwie poprawki w backendzie znalezione przez testy integracyjne** (do przejrzenia przez Mikołaja):
- `pom.xml`: zamiast samego `flyway-core` jest `spring-boot-starter-flyway`. W Spring Boot 4 autokonfiguracja
  Flyway jest w osobnym module, więc migracje w ogóle się nie uruchamiały, a aplikacja nie wstawała na pustej bazie
  („missing table auth_challenges”). Dotyczy też zwykłego startu backendu, nie tylko testów.
- `application-test.yaml`: adres kontraktu w cudzysłowie. Bez niego YAML czyta `0x5fbd...` jako liczbę,
  domena EIP-712 wychodzi inna i poprawny podpis z wektora dostawał `403 SIGNER_MISMATCH`.

**Narzędzia na komputerze Kuby**
Zainstalowano w katalogu domowym (bez roota): Java 21 (Temurin) i Maven 3.9.9, po sprawdzeniu sum kontrolnych.
Nie zmieniano `~/.bashrc` ani ustawień systemu. Żeby z nich korzystać, w terminalu:
`export JAVA_HOME=$HOME/.local/opt/jdk21 PATH=$HOME/.local/opt/jdk21/bin:$HOME/.local/opt/maven/bin:$PATH`

## Jak to sprawdzono

- `./mvnw verify` w `backend/` na PostgreSQL z Compose: 47 testów jednostkowych i 3 integracyjne, wszystkie zielone
  (wektor, testy mutacji, migracje Flyway, `POST /stages` -> `202` z idempotentnym powtórzeniem, zły podpis -> `403`).
  Jak uruchomić: [infrastructure/local/README.md](../../../infrastructure/local/README.md).
- Schemat raportu przetestowano na 15 przykładach (4 poprawne, 11 błędnych) - wszystkie zachowały się zgodnie z oczekiwaniem.
- OpenAPI: YAML się parsuje, wszystkie `$ref` się rozwiązują, certyfikat z `shared/fixtures/` przechodzi
  kopertę. Pełny walidator OpenAPI 3.1 po zmianach z 2026-11-02 nie był uruchamiany.
- `docker compose up -d`: trzy usługi `healthy`, sprawdzone także z komputera (RPC Anvila, API Kubo).

## Do rozstrzygnięcia na DEC-02 (piątek)

**Rozstrzygnięte 2026-11-02 (DEC-02: e-bike).** Zachowano profil z `telemetry.schema.json`, a wektor
podpisu przegenerowano pod profil etapu 2 (`inspectedUnits`, `defectsFound`, `checklistItemsTotal`,
`checklistItemsPassed`); backend i Solidity przechodzą na nowym wektorze. Opis pierwotnego problemu:
wektor Roberta miał pomiary `temperatureMilliC`, `torqueMilliNm`, `pressurePa`, a profil etapu 2
wymagał innych kluczy, więc backend z walidacją profilu odrzuciłby taki raport.

## Czeka na innych albo na dostęp - celowo nie ruszone

| Co | Na kogo / co czeka |
|---|---|
| Badanie Pinaty (CID, limity darmowego planu) | Konto i token Pinata ma **Mikołaj**. Tokenu nie należy wpisywać do repozytorium. |
| Zatwierdzenie OpenAPI i schematów | Przegląd w tygodniu 2 (profile pomiarów zatwierdzone w DEC-02, 2026-11-02). |
| Merge gałęzi Roberta (`robert/week1-simulator-vector`) | Zrobione (PR #4); wektor w `backend/src/test/resources/fixtures/` jest identyczny z `shared/fixtures/`. |

## Do potwierdzenia przez zespół

- Dwa endpointy dodane jako **propozycja** (oznaczone w pliku): rejestracja certyfikatu stacji
  i odczyt bieżącej sesji. Protokół wymaga zarejestrowanego certyfikatu, ale nie mówi, jak go zarejestrować.
- Lista kodów błędów to propozycja do przeglądu z Mikołajem. Backend ma na razie tylko część z nich.
- Wybrano OpenAPI 3.1. Jeśli generator klienta we frontendzie go nie obsługuje, trzeba zmienić na 3.0.
