# Scenariusz referencyjny MVP: stacja montażowa roweru elektrycznego (e-bike)

**Status:** zatwierdzony 2026-11-02 (DEC-01 uzupełnione o scenariusz e-bike, DEC-02: profil
e-bike v1, patrz [decisions](decisions.md)). Nie zmienia protokołu, kontraktu ani
podpisów: to scenariusz wypełniający ogólną marszrutę 0..3. Wartości pomiarów są
**demonstracyjne**, nie normy.

## 1. Cel

MVP pokazuje na jednym konkretnym wyrobie, **montażu roweru elektrycznego**, pełną ścieżkę:
raport stacji podpisany jej kluczem -> weryfikacja w backendzie -> zapis hashu na blockchainie
i raportu w IPFS -> paszport produktu z QR. Konkretny wyrób nadaje danym sens (co mierzy
stacja, dlaczego kontrolę zatwierdza człowiek), ale architektura pozostaje niezależna
od produktu.

Dlaczego e-bike: ma krótką, zrozumiałą marszrutę, mierzalne parametry montażu (momenty
dokręcenia, napięcie, hamulce), element krytyczny dla bezpieczeństwa (hamulce, bateria),
który uzasadnia drugi, niezależny podpis człowieka na kontroli, oraz realny kontekst
śledzenia (numer ramy, partia baterii). Normą odniesienia dla rowerów wspomaganych
elektrycznie jest EN 15194; scenariusz **nie deklaruje zgodności** z żadną normą.

## 2. Marszruta e-bike na etapach 0..3

Etapy, numeracja i reguły przejść pozostają zgodne z [production-rules](production-rules.md).

| stageId / kod | Stacja (przykład) | Co poświadcza raport | Klucze pomiarów (profil v1, liczby całkowite) |
|---|---|---|---|
| 0 / PREPARATION | Stacja przygotowania | komplet części (rama, widelec, koła, silnik, bateria, osprzęt) i sprawdzenie narzędzi | `checklistItemsTotal`, `checklistItemsPassed` |
| 1 / ASSEMBLY | Stacja montażu | montaż silnika, baterii, kół, hamulców, dokręcenie połączeń | `operationsTotal`, `operationsCompleted`, `cycleTimeMs` |
| 2 / QUALITY_CONTROL | Stanowisko kontroli | test hamulców, napędu i elektryki, ocena wizualna; **podpis operatora zmiany (`StageApproval`)** | `inspectedUnits`, `defectsFound`, `checklistItemsTotal`, `checklistItemsPassed` |
| 3 / PACKAGING | Stacja pakowania | zabezpieczenie do transportu, etykieta z numerem partii | `unitsPacked`, `labelsApplied` |

Profile (zakresy, klucze wymagane, `additionalProperties: false`) są w
[telemetry.schema.json](../shared/schemas/telemetry.schema.json) (`profile*V1`) i są
źródłem prawdy; ta tabela tylko je opisuje. Reguły oceny `qaPassed`:

| Etap | `qaPassed=true` tylko gdy |
|---|---|
| 0 | `checklistItemsPassed == checklistItemsTotal` |
| 1 | `operationsCompleted == operationsTotal` |
| 2 | `defectsFound == 0` i `checklistItemsPassed == checklistItemsTotal` |
| 3 | `labelsApplied == unitsPacked` |

Przykład na etapie 2: kontrola 10 rowerów z partii, 12 punktów listy (hamulce, napęd,
elektryka, oświetlenie, wygląd), 0 wad, 12/12 zaliczonych -> `qaPassed=true` i wymagany drugi podpis.
Wada lub niezaliczony punkt -> `qaPassed=false` -> `StageRejected` bez przesunięcia partii
(DEC-15); poprawka i nowy raport według [reguły zastępowania](corrections.md).

**DEC-02 zatwierdzone 2026-11-02:** profil e-bike z powyższymi kluczami jest zamrożonym
zestawem v1; wartości są demonstracyjne, nie normy. Wspólny wektor podpisu
(`shared/fixtures/signature-vector.json`, etap 2) używa dokładnie tych kluczy i przechodzi
schemat (test w `simulator/tests/test_signature_vector.py`).

Pomiary fizyczne (moment dokręcenia, ciśnienie ogumienia, napięcie baterii itp.) są
naturalnym rozszerzeniem profilu w przyszłej wersji (pkt 4.3), a nie częścią v1.

## 3. Mapowanie na elementy systemu

| Element systemu | Rola w scenariuszu e-bike |
|---|---|
| Partia (`batchNumber` `B-RRRR-NNNN`) | seria rowerów jednego modelu produkowanych razem |
| Stacja (adres EVM + certyfikat) | jedno stanowisko; rejestr on-chain ogranicza je do swoich etapów (`stageMask`) |
| Operator zmiany (`QA_INSPECTOR_ROLE`) | pracownik kontroli, drugi klucz na etapie 2 |
| Symulator | zastępuje maszyny i PLC: generuje pomiary, podpisuje raport kluczem stacji, wysyła `POST .../stages` |
| Paszport publiczny | historia partii: etapy, czasy, hashe, CID raportów; brak danych wewnętrznych |

W produkcji rolę symulatora pełniłby konektor (bramka edge) odbierający dane z PLC przez
OPC UA lub MQTT i podpisujący raport kluczem stacji. Integracja z PLC jest **poza zakresem**
MVP (zgodnie z [source-alignment](source-alignment.md)).

## 4. Wymaganie: możliwość rozszerzenia na inne produkty

System ma być projektowany jako **niezależny od produktu**. Wiedza o e-bike żyje wyłącznie
w profilu pomiarów i scenariuszach symulatora, a nie w logice kontraktu, kolejki ani API.

### 4.1. Co jest ogólne (nie zmienia się przy nowym produkcie)

- **Kontrakt:** zna tylko partię, `stageId` (uint32), `reportHash`, `qaPassed`, rejestr stacji
  i role. Nie zna pomiarów ani nazwy produktu. Liczba etapów jest stałą kontraktu (obecnie 4);
  produkt z innym przebiegiem wymaga świadomej zmiany kontraktu i ABI, ogłoszonej zespołowi.
- **Protokół i podpisy:** `StageCompletion`/`StageApproval`, JCS, Keccak-256, EIP-712.
- **Backend:** walidacja podpisu, idempotencja, kolejka poleceń, relayer, indexer.
- **Certyfikaty i RBAC:** niezależne od wyrobu.

### 4.2. Co jest specyficzne dla produktu (miejsce rozszerzenia)

| Element | Gdzie | Zasada |
|---|---|---|
| Profil pomiarów etapów 0..3 (klucze, jednostki, zakresy) | `shared/schemas/telemetry.schema.json` | jeden zamrożony profil na wdrożenie (v1); nowy produkt = nowy profil |
| Reguły oceny `qaPassed` | backend (walidator profilu) | wyłącznie po zatwierdzonym profilu; brak profilu = blokada, nie PASS |
| Nazwy etapów i opisy | konfiguracja / UI | kody `PREPARATION`..`PACKAGING` pozostają stabilne |
| Scenariusze i generatory pomiarów | `simulator/scenarios/` | jeden plik YAML na produkt |
| Teksty paszportu | frontend | etykiety i jednostki z profilu |

### 4.3. Zasady projektowe, żeby rozszerzenie było tanie

1. **Profile jako dane, nie kod.** Walidator czyta profil (klucze, typ całkowity, min/max)
   z jednego źródła; dodanie produktu nie wymaga nowych klas ani zmian kontraktu.
2. **Brak nazw pomiarów w kontrakcie, bazie i kolejce.** Pomiary przechodzą jako obiekt
   w raporcie JCS; tabele nie mają kolumn typu `torque`.
3. **Zmiana podpisywanych pól wymaga ogłoszenia** i dotyka Pythona, Javy i TS. Dlatego
   identyfikator profilu/produktu **nie jest dziś w podpisanym raporcie**.
4. **Jeśli system ma obsługiwać kilka produktów jednocześnie** (poza MVP), trzeba świadomie
   dodać identyfikator profilu (np. `profileId` + wersja) w partii lub w raporcie v2
   (`schemaVersion` 2), zaktualizować wspólny wektor i testy we wszystkich modułach.
   W MVP wdrożenie obsługuje jeden profil.
5. **Wersjonowanie:** zmiana profilu zmienia `schemaVersion` lub wersję profilu; stare raporty
   pozostają weryfikowalne według profilu, z którym zostały podpisane.

### 4.4. Przykładowe przyszłe produkty

| Produkt | Co się zmienia | Co zostaje |
|---|---|---|
| Pakiet baterii | inne pomiary (napięcia ogniw, rezystancja izolacji), ewentualnie paszport wg przepisów | cały kontrakt, protokół, backend |
| Elektronarzędzia, AGD | inne momenty/testy funkcjonalne | j.w. |
| Wyrób z inną liczbą etapów | stała liczby etapów w kontrakcie, `stageMask`, UI | architektura, podpisy, relayer |

## 5. Wpływ na prace tygodnia 1-2

- **Robert:** scenariusz YAML "e-bike" jako pierwszy scenariusz symulatora; generatory
  pomiarów zgodne z profilami v1 (np. etap 2: wada lub niezaliczony punkt -> `qaPassed=false`).
- **Kuba:** profile w `telemetry.schema.json` są źródłem prawdy; zmiana wymaga ogłoszenia
  i nowego wektora (właściciel: Robert).
- **Adam, Mikołaj:** bez zmian w kontrakcie; backend (Mikołaj) dopisuje walidator profilu
  wg tabeli reguł `qaPassed` z pkt 2.
- **Frontend (tyg. 2+):** etykiety etapów i jednostek z profilu, nazwa produktu w nagłówku
  paszportu z konfiguracji.

## 6. Poza zakresem

Zgodność z EN 15194 lub inną normą, integracja z PLC/OPC UA/MQTT, realne tolerancje
producenta, obsługa wielu produktów naraz w jednym wdrożeniu, cyfrowy paszport baterii
wymagany przepisami UE (dla baterii rowerowych nie zakładamy tego wymogu bez sprawdzenia
aktualnych przepisów).
