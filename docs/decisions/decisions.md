# Założenia, decyzje i granice MVP

**Status:** po analizie 2026-10-27 wprowadzono zmiany [v2](../project/changes-v2.md).
Pozycje oznaczone `USTALONA` zatwierdził użytkownik 2026-10-27; zespół
potwierdził je na spotkaniu startowym **2026-11-02** (Adam, Mikołaj, Kuba, Robert;
wpis do zatwierdzenia w PR). Osoby 1/2/3/4 odpowiadają modułom z głównego README.
Wymagania PDF mają pierwszeństwo: dwa klucze przy zamknięciu kontroli,
certyfikat każdej stacji, minimalny zapis on-chain i 12 tygodni nie są
opcjonalne. Szczegóły wykonawcze są poniżej.

**Aktualizacja 2026-11-11:** wybrano wariant 1 (DEC-17): lokalny system
Anvil/Kubo oraz dodatkowe jednorazowe demo samego kontraktu na Amoy.
Decyzja zastępuje wcześniejsze wymagania pełnej integracji Amoy/Pinata,
także w historycznych planach tygodni 1–2 i dokumencie zmian v2.

## Jak używać

Przed rozpoczęciem zadania sprawdź wskazane decyzje. Zmiana wariantu wymaga
poprawy diagramów, protokołu, schematów, fixture'ów i planu w jednym PR.
Nie odgaduj decyzji w kodzie. Status `DO POTWIERDZENIA` oznacza wartość roboczą
do zatwierdzenia przez zespół w wyznaczonym terminie, nie zgodę na zmianę w kodzie.

## Rejestr

| ID / status | Decyzja | Właściciel | Co blokuje |
|---|---|---|---|
| DEC-01 / USTALONA (uzupełnione 2026-11-02) | Ogólna marszruta przygotowanie -> montaż -> kontrola -> pakowanie, stageId 0..3, bramka dwóch kluczy na etapie 2. Architektura i kontrakt niezależne od produktu, bez norm fabrycznych. Scenariusz referencyjny MVP: montaż roweru elektrycznego (e-bike), z możliwością rozszerzenia na inne produkty ([scenariusz](../project/product-scenario.md)) | cały zespół | [reguły produkcji](../rules/production-rules.md), [scenariusz e-bike](../project/product-scenario.md) |
| DEC-02 / USTALONA (2026-11-02, Adam, Mikołaj, Kuba, Robert) | Całkowite jednostki; jeden zamrożony zestaw profili v1 dla etapów 0..3 w wariancie **e-bike** (klucze i zakresy: `profile*V1` w [telemetry.schema.json](../../shared/schemas/telemetry.schema.json), opis: [scenariusz](../project/product-scenario.md)). Wartości demonstracyjne, nie normy. Wspólny wektor podpisu dostosowany do profilu etapu 2 | 2 + 4, konsultacja 1 | schema, walidacja, generatory |
| DEC-03 / USTALONA | JCS + Keccak + EIP-712 `StageCompletion` **bez nonce**; CID jest osobnym wskazaniem relayera. Ręczna implementacja EIP-712 w Javie, wspólny wektor w 4 językach (V2-01, V2-10) | 1 + 2 + 4 | wektory podpisów |
| DEC-04 / USTALONA | Dwa klucze: EIP-712 stacji + **podpis EIP-712 `StageApproval` operatora poza łańcuchem**, weryfikowane razem w `completeStage`. Adresy różne, aktualna rola QA. Brak `approveStage` on-chain (V2-03) | 1 + 2 + 3 | [dwa klucze](../rules/quality-approval.md) |
| DEC-05 / USTALONA | SIWE (EIP-4361) tylko do wydania opaque tokenu Bearer (w bazie skrót SHA-256). Brak cookie i CSRF. Wartości TTL robocze (DEC-11) (V2-06) | 2 + 3 + 4 | [logowanie](../rules/access-control.md), OpenAPI |
| DEC-06 / USTALONA | OPERATOR_ROLE dla create w API, RELAYER_ROLE dla wykonania on-chain, QA_INSPECTOR_ROLE dla operatora kontroli. **Brak STATION_ROLE** - zastępuje ją rejestr stacji (DEC-13). Domyślna odmowa | 1 + 2 + 3 | [macierz uprawnień](../rules/access-control.md) |
| DEC-07 / USTALONA (2026-11-11, wariant 1) | Jedna aktywna operacja na `(deployment, batchId, stageId)`. Lokalny indexer: `latest`, 1 potwierdzenie (blok transakcji jest pierwszym), okno ponownego skanu i porównanie block hash. Nie wymagamy indexera Amoy ani pomiarów jego finalności; skrypt dema sprawdza receipts i zapisuje bloki/hash transakcji | 1 + 2 + 4 | finalność lokalna, deadline |
| DEC-08 / USTALONA | PostgreSQL jako trwała kolejka (`FOR UPDATE SKIP LOCKED`), jedna instancja relayera i scheduler indexera. Bez Redis/RabbitMQ. **Bez fee-bump transakcji** (V2-07) | 2 | persistence, worker |
| DEC-09 / USTALONA (2026-10-27) | Wariant C uproszczony: nowy raport etapu dopiero, gdy stary nie może się już wykonać (deadline minął w potwierdzonym bloku). Bez nonce i bez dodatkowych funkcji kontraktu. `maxDeadlineWindow` egzekwuje kontrakt. Opis: [corrections](../rules/corrections.md) (V2-02) | 1 + 2 + 4 | T026 |
| DEC-10 / USTALONA (zakres zaktualizowany 2026-11-11) | Symulator odczytuje z kontraktu przez lokalne RPC Anvila wyłącznie stan partii (`getBatch`, `nextStage`); nonce stacji nie istnieje. Nie wymagamy scenariuszy symulatora na Amoy | 1 + 4 | konfiguracja symulatora |
| DEC-11 / USTALONA (2026-11-02; sieć zaktualizowana 2026-11-11) | `maxDeadlineWindow` 3600 s, `approvalDeadline` domyślnie jak deadline stacji, token 8 h / 30 min bezczynności, challenge 5 min; limity API i retry/backoff ustalane przed odbiorem. Minimalny priority fee relayera konfigurowalny dla Anvila. Historyczne 30 gwei z Amoy nie jest wymogiem lokalnym ani gwarancją aktualnego progu; opłaty Amoy dobiera tylko skrypt dema | 2 + 3 + 4 | odporność i odbiór |
| DEC-12 / USTALONA | Numer partii `B-RRRR-NNNN` nadawany przez backend; `batchId = keccak256(utf8(batchNumber))`, liczony też on-chain w `createBatch(string)`. QR i ręczne wpisanie używają numeru (V2-11). Wersje zależności przypinane przy inicjalizacji | 2, cały zespół | CreateBatchRequest |
| DEC-13 / USTALONA | Rejestr stacji on-chain (`registerStation`/`revokeStation`, `expiresAt`, `stageMask`) jest źródłem egzekucji. JSON certyfikatu: payload + EIP-712 wystawcy jako poświadczenie audytowe zgodne z rejestrem. Aktywacja po potwierdzonej rejestracji. Nie deklarujemy X.509 ani TPM (V2-04) | 2 + 4, 1 (kontrakt i Java) | [certyfikat](../rules/station-identity.md) |
| DEC-14 / USTALONA (zaktualizowana 2026-11-11) | Magazyn raportów: port `ReportStorage`, Kubo w rozwoju, testach i demonstracji; bez obowiązku adaptera Pinata. Upload bajtów JCS jako plik; walidator CID przyjmuje CIDv0 i CIDv1. Demo kontraktu Amoy nie gwarantuje publicznego przechowania raportu | 2 | IPFS, CI |
| DEC-15 / USTALONA | Odrzucenie kontroli zapisywane on-chain (`StageRejected`), bez przesunięcia partii; HTTP `202`, `outcome=REJECTED` (V2-05) | 1 + 2 | kontrakt, historia |
| DEC-16 / USTALONA (zaktualizowana 2026-11-11) | Środowiska: Anvil/Kubo dla całej aplikacji, lokalna integracja pionowa w tyg. 5, próba lokalnej prezentacji i dodatkowe demo kontraktu Amoy w tyg. 10. Bez wdrażania aplikacji na Amoy/Pinata. Lokalny HTTPS dla frontendu; zasada wyboru wspieranej wersji Spring Boot bez zmian | cały zespół | plan |
| DEC-17 / USTALONA (2026-11-11) | **Wariant 1:** cały system lokalnie; na Amoy jednorazowy deploy i pełny przebieg partii skryptem, bez backendu, UI, indexera Amoy, mostu i kotwiczenia. Demo dodatkowe, nie blokuje odbioru lokalnego MVP ani CI; niewykonanie wymaga jawnego odnotowania. [Zakres i alternatywy](local-blockchain-scope.md) | cały zespół; demo: 1, przegląd: 4 | aktualny zakres MVP i T029 |

## Dlaczego QA potrzebuje osobnej kolejki

Na łańcuchu raport nie jest ukończonym etapem przed `completeStage`.
Inspektor szukający tylko potwierdzonych `StageCompleted` nigdy nie zobaczyłby
raportu do zatwierdzenia. Kandydat istnieje wcześniej w bazie, po pinowaniu,
i jest dostępny wyłącznie uprawnionej obsłudze/QA. Publiczna historia go nie zawiera.
Operator zatwierdza go podpisem `StageApproval`, który backend zapisuje i
przekazuje kontraktowi razem z podpisem stacji. Przebieg: [flows](../architecture/flows.md).

## Cztery rodzaje podpisów

- **Login (SIWE, EIP-191):** wydanie tokenu Bearer; nie autoryzuje zdarzeń.
- **StageCompletion (EIP-712):** podpis stacji pod konkretnym raportem.
- **StageApproval (EIP-712):** podpis operatora zmiany pod tym samym `reportHash`
  (bez gazu operatora).
- **Transakcja relayera:** podpis serwera; nie zastępuje podpisu stacji ani operatora.

Każdy ma osobny typ lub schemat podpisu, więc jednego nie da się użyć jako drugiego.

## Co można robić przed potwierdzeniem

Inicjalizacja narzędzi, layout UI, wektory, szkielet kontraktu i API.
Profile DEC-02 i wartości DEC-11 potwierdzono 2026-11-02; DEC-07 zamknięto
2026-11-11 dla lokalnego zakresu. Nie ma bramki pełnego odbioru aplikacji na Amoy.
Wdrożenie fizycznej linii nie jest zadaniem MVP.
