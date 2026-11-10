# Tydzień 1 - plan Kuby (schematy, Java, infrastruktura)

Wersja „po ludzku” do [week1-plan.md](week1-plan.md). Techniczne tabele są tam;
tutaj opisuję, **co właściwie robisz, po co i po czym poznasz, że działa**.

## Twoja rola w jednym zdaniu

Zamiast frontendu (który startuje w tygodniu 2) w tym tygodniu przygotowujesz
**fundamenty, na których oprze się cały zespół, w tym Twój własny frontend**:
umowę API (OpenAPI), formaty danych, wspólną „matematykę” podpisów w Javie
oraz środowisko lokalne (`docker compose up`).

Dlaczego to dobre dla frontendu: w tygodniu 2 dostaniesz gotowe OpenAPI, z którego
wygenerujesz mocki i klienta, gotowe profile pomiarów do ekranów oraz działający
lokalny blockchain i bazę.

## Słowniczek

| Słowo | Co to znaczy po ludzku |
|---|---|
| OpenAPI | Plik YAML opisujący każdy endpoint backendu: adres, co przyjmuje, co zwraca, jakie błędy. To „umowa” między backendem a frontendem i symulatorem. |
| JSON Schema | Plik opisujący, jak ma wyglądać dokument JSON (jakie pola, jakie typy, co jest wymagane). Odrzuca nieznane pola. |
| Profil pomiarów | Dla każdego etapu: jakie dokładnie pomiary raport zawiera, w jakich jednostkach (liczby całkowite), jakie są dozwolone granice. Bez niego backend nie wie, co znaczy „raport poprawny”. |
| JCS (RFC 8785) | Sposób zapisania JSON-a tak, żeby **każdy język dał dokładnie te same bajty** (kolejność kluczy, spacje, liczby). Bez tego hash raportu byłby w Pythonie inny niż w Javie. |
| Keccak-256 | Funkcja skrótu używana w Ethereum. **Nie jest tym samym co „SHA3-256”**, mimo podobnej nazwy. To częsta pułapka. |
| EIP-712 i „odzysk adresu” | Podpis zawiera w sobie informację, kto go złożył. Z podpisu i wiadomości można **obliczyć adres autora**. Backend robi to, żeby sprawdzić, czy raport podpisała znana stacja. |
| Test mutacji | Test, który zmienia jedno pole raportu i sprawdza, że hash i podpis przestają pasować. Dowód, że żadne pole nie jest „poza ochroną”. |
| Docker Compose | Jedna komenda uruchamiająca kilka usług naraz. U nas: PostgreSQL (baza), Anvil (lokalny blockchain), Kubo (lokalny węzeł IPFS). |
| Health check | Automatyczne sprawdzenie, czy usługa naprawdę działa, a nie tylko „wystartowała”. |
| IPFS, CID | IPFS to rozproszony magazyn plików. CID to „adres” pliku wyliczony z jego zawartości. Kubo to lokalny węzeł IPFS, a Pinata to usługa w chmurze. |
| Port `ReportStorage` | Interfejs Javy: „zapisz bajty raportu, oddaj CID”. Dzięki niemu backend nie wie, czy pod spodem jest Kubo czy Pinata. |

## Co będzie istniało w piątek wieczorem

- `shared/schemas/openapi.yaml` zatwierdzony przez Mikołaja i zespół.
- `telemetry.schema.json` i `eip712.json` zgodne z [protokołem](../../protocol.md).
- Szkic **profili pomiarów** dla etapów 0..3 (razem z Robertem), do zaakceptowania w piątek.
- Moduł Javy w `backend/.../infrastructure/blockchain/`: JCS, Keccak-256 i **odzysk adresu z podpisu** dla `StageCompletion` i `StageApproval`, sprawdzony na wektorze od Roberta i testami mutacji.
- `infrastructure/local/compose.yaml` + `.env.example` + health checki + opis „jak świadomie wyczyścić środowisko”.
- Notatka z badania IPFS: co dokładnie robi Kubo i Pinata, jaka wersja CID wychodzi, jakie są limity darmowego planu.
- Szkic interfejsu `ReportStorage` (bez implementacji).

## Dzień po dniu

### Poniedziałek - umowa API

**Co robisz:** piszesz pierwszy szkic `shared/schemas/openapi.yaml`:
- logowanie: `POST /auth/challenges`, `POST /auth/sessions`;
- partie: `POST /batches`, `GET /batches/{batchId}`, `GET /batch-numbers/{batchNumber}`, historia;
- etapy: `POST /batches/{batchId}/stages`, `GET /commands/{commandId}`;
- kolejka QA: lista kandydatów, szczegóły, `POST .../approval`;
- format błędów (Problem Details) i statusy polecenia ze [model danych](../../data-model.md).

Wszystko oparte na [protokole](../../protocol.md): nie wymyślasz nic nowego, tylko przepisujesz do YAML.

**Po co:** od tego zależą Mikołaj (kontrolery), Robert (klient w symulatorze) i Ty w tygodniu 2 (mocki frontendu). Im wcześniej jest szkic, tym wcześniej mogą go krytykować.

**Poznasz, że działa:** plik otwiera się w podglądzie OpenAPI bez błędów, a Mikołaj ma go do przeglądu we wtorek rano.

### Wtorek - formaty danych i profile pomiarów

**Co robisz:**
- `telemetry.schema.json`: jak wygląda raport (`schemaVersion`, `batchId`, `stageId`, `stationAddress`, `timestamp`, `measurements`, `qaPassed`), z zakazem nieznanych pól.
- `eip712.json`: dokładne typy `StageCompletion` i `StageApproval` (pola w tej kolejności, co w protokole).
- Z Robertem: szkic profili pomiarów etapów 0..3.

**O profilach:** nie wymyślamy norm fizycznych (nie mamy konkretnego produktu). Profil to np. „ten etap zgłasza `checklistItemsTotal` i `checklistItemsPassed` jako liczby całkowite, oba >= 0, a `qaPassed` jest prawdą tylko, gdy wszystkie pozycje są zaliczone”. Dobrać trzeba klucze, jednostki całkowite i granice, ale jako **demonstracyjne**, z opisem, że nie są normą.

**Po co:** bez tego nie da się napisać walidatora w backendzie, generatora w symulatorze ani formularzy we frontendzie.

**Poznasz, że działa:** schematy walidują przykładowy raport, a nieprawidłowy (brak pola, nieznane pole, ułamek) odrzucają.

### Środa - najtrudniejszy dzień: krypto w Javie

**Co robisz:** po wtorkowym wieczorze Robert opublikuje `signature-vector.json` - plik z dokładnym raportem, oczekiwanymi bajtami po kanonizacji, oczekiwanym hashem i podpisami. Ty piszesz w Javie moduł, który dla tego raportu:
1. kanonizuje JSON (JCS) i dostaje **te same bajty** co w pliku;
2. liczy Keccak-256 i dostaje **ten sam hash**;
3. liczy digest EIP-712 i **odzyskuje adres stacji** równy temu z pliku;
4. to samo dla podpisu operatora (`StageApproval`).

Implementujesz EIP-712 **ręcznie** dla naszych dwóch stałych struktur (zamiast ufać gotowemu koderowi, który bywa zawodny dla niektórych typów). Do pomocy: biblioteka do JCS w Javie i moduł krypto Web3j do Keccak i odzysku podpisu.

W środę pracujecie **w parze z Mikołajem**: on robi spike transakcji, Ty krypto. Na koniec dnia pokaż mu API modułu, żeby mógł go użyć w czwartek.

**Po co:** to jest ryzyko numer jeden całego projektu: cztery języki muszą dawać identyczny wynik. Jeśli tu się rozjedzie, nic dalej nie zadziała. Wcześniej odkryty błąd kosztuje godziny, później tygodnie.

**Poznasz, że działa:** test w Javie jest zielony dla wektora.

**Jeśli wynik się nie zgadza:** najczęstsze przyczyny to JCS (spacje, kolejność), pomylenie Keccak z SHA3-256, zła kolejność pól w typie EIP-712 albo adres zapisany wielkimi literami. Skontaktuj się z Robertem; wektor jest wspólny, więc nie „naprawiaj po swojemu”.

### Czwartek - domknięcie krypto i Compose

**Rano - testy mutacji i przekazanie:**
- Dla każdego pola raportu zmieniasz je i sprawdzasz, że hash oraz odzyskany adres się zmieniają.
- Ten sam adres zapisany małymi i dużymi literami ma dać ten sam wynik.
- Przekazujesz moduł Mikołajowi (ma być gotowy do południa, bo od niego zależy `POST /stages`).

**Po południu - `docker compose up`:**
- `infrastructure/local/compose.yaml` z trzema usługami: PostgreSQL, Anvil, Kubo.
- `.env.example` z pustymi sekretami i opisem zmiennych.
- Health checki: baza przyjmuje połączenie, Anvil odpowiada na zapytanie o `chainId` (31337), Kubo odpowiada na swoje API.

**Po co:** wszyscy mają mieć **identyczne środowisko jedną komendą**, bez „u mnie działa”. W tygodniu 2 odpalisz na tym swój frontend.

**Poznasz, że działa:** `docker compose up` kończy się trzema zdrowymi usługami.

### Piątek - Compose do końca i badanie IPFS

**Co robisz:**
- Opis w README: jak **świadomie** zresetować środowisko (po restarcie Anvila stary stan bazy nie pasuje do nowego łańcucha) - nic nie kasuje się automatycznie.
- Badanie magazynu raportów:
  - wysyłasz **dokładne bajty JCS jako plik** do Kubo i do Pinaty (nie obiekt JSON, bo usługa mogłaby go zserializować po swojemu);
  - sprawdzasz, jakiej wersji jest zwrócony CID (v0 czy v1), czy plik da się pobrać przez bramkę i czy pobrany JSON po JCS daje ten sam hash;
  - zapisujesz limity darmowego planu Pinaty (liczba plików, requesty), bo pętla symulatora może je szybko zużyć.
- Szkic interfejsu `ReportStorage` (jedna metoda: bajty -> CID) i opis błędów (timeout, brak usługi).
- Piątkowe demo i zamknięcie profili pomiarów z zespołem.

**Po co:** zanim ktoś napisze adapter Pinaty (tydzień 4), wiemy, jak naprawdę działa, ile kosztuje i co może się zepsuć.

**Poznasz, że działa:** masz notatkę z konkretnymi wynikami (wersja CID, limity, przykładowe CID) oraz interfejs do przeglądu.

## Od kogo czekasz i kto czeka na Ciebie

- **Czekasz na:** Roberta - wektor (wtorek wieczorem); Mikołaja - recenzja OpenAPI (wtorek).
- **Czekają na Ciebie:** Mikołaj - OpenAPI (poniedziałek) i moduł krypto (czwartek do południa); Robert - schematy (wtorek) i compose (czwartek); Adam - wynik Twojego modułu do przeglądu w piątek.

## Czego w tym tygodniu NIE robisz

- Nie ruszasz frontendu: ani `npm create vite`, ani wagmi, ani QR (to poniedziałek tygodnia 2: T005 i T032). Wektor w TypeScripcie także w tygodniu 2.
- Nie piszesz adaptera Pinaty ani Kubo w produkcyjnej wersji (tydzień 4) - tylko badanie i interfejs.
- Nie zmieniasz pól podpisu bez ogłoszenia.

## Jeśli się zablokujesz

- Java nie jest Twoim językiem -> to zamierzone: Mikołaj siedzi obok w środę, a Adam przegląda wynik w piątek. Krypto ma mały, dobrze zdefiniowany zakres (dwie struktury), a wektor mówi dokładnie, co ma wyjść.
- Docker nie startuje na Macu -> sprawdź architekturę obrazów (Apple Silicon) i napisz na kanale, zanim stracisz pół dnia.
- Pinata prosi o płatny plan -> zapisz, co jest dostępne za darmo, i zostaw Kubo jako domyślny magazyn.
