# Tożsamość i certyfikat symulowanej stacji

## Wymaganie źródłowe

PDF wymaga własnej pary kluczy i certyfikatu dla każdej stacji w symulacji
(s. 2), oraz podpisów stacji i operatora przy zamknięciu kontroli (s. 3).
Nie wymaga instalacji TPM, sterownika PLC, pełnej infrastruktury PKI ani
certyfikacji sprzętu. Nie deklaruj rzeczywistej atestacji sprzętowej.

## Wariant MVP - DEC-13 (zmiana V2-04)

Dwie warstwy, które muszą być zgodne:

1. **Rejestr on-chain (egzekucja):** `registerStation(address, stationId, expiresAt,
   stageMask)` i `revokeStation(address)` wywołuje admin. Kontrakt odrzuca
   `completeStage` stacji nieaktywnej, wygasłej lub z `stageId` spoza `stageMask`.
   Zastępuje to `STATION_ROLE` i przenosi zakres etapów oraz ważność do kontraktu.
2. **Certyfikat aplikacyjny (poświadczenie audytowe, wymóg PDF):** podpisany
   przez wystawcę JSON przechowywany off-chain. To **certyfikat aplikacyjny**,
   nie X.509 do TLS. Jeżeli prowadzący wymaga X.509/mTLS, zatwierdź zmianę przed
   implementacją.

Pola certyfikatu: wersja, stationId, publiczny adres EVM stacji, identyfikator
wystawcy, serial, issuedAt, expiresAt oraz dozwolone etapy (`allowedStageIds`,
odpowiadające `stageMask`). Wystawca podpisuje poświadczenie osobnym typem EIP-712.
Nie weryfikuj certyfikatu jako StageCompletion.
Backend uznaje stację za ACTIVE tylko gdy certyfikat jest poprawny **i** zgadza się
z rejestrem on-chain (adres, `stationId`, `expiresAt`, maska).

## Cykl życia

1. Narzędzie lokalne generuje klucz stacji, bez wypisywania sekretu do logów.
2. Administrator sprawdza adres/stanowisko i wystawia certyfikat (CLI w Pythonie).
3. Admin rejestruje stację w kontrakcie (`registerStation`) z tymi samymi
   `stationId`, `expiresAt` i maską etapów.
4. Backend rejestruje certyfikat i weryfikuje wystawcę, ważność, unikalność
   serialu i zgodność z rejestrem on-chain (odczyt/zdarzenia), a przy zgłoszeniu
   także zgodność adresu z odzyskanym signerem raportu.
5. Stacja podpisuje raport swoim kluczem. W żądaniu podaje `stationCertificateId`
   poza raportem objętym JCS; backend wybiera zapisany, zweryfikowany certyfikat.
6. Kontrakt niezależnie weryfikuje podpis EIP-712 i aktywną rejestrację.
7. Przy unieważnieniu admin wywołuje `revokeStation` (jedna operacja on-chain
   blokująca stację bez względu na backend) i oznacza certyfikat jako REVOKED.
8. Rotacja tworzy nowy klucz/certyfikat i nową rejestrację; historycznych
   raportów nie przepisujemy na nowy adres.

Klucz wystawcy nie jest kluczem relayera. Backend może tylko przechowywać
publiczne poświadczenia i weryfikować podpis wystawcy; wystawianie jest
kontrolowanym narzędziem administracyjnym, nie publicznym endpointem.

## Rozmieszczenie plików

- Schemat publiczny: przyszły `shared/schemas/station-certificate.schema.json`.
- Wspólny przykład testowy: `shared/fixtures/`, bez rzeczywistych sekretów.
- Symulator: `identity.py` i lokalna konfiguracja wskazująca certyfikat.
- Backend: weryfikator i rejestr w istniejących pakietach application/
  infrastructure/persistence oraz migracja tabeli certyfikatów.
- Sekrety i rzeczywiste dane lokalne: poza repozytorium lub w ignorowanym
  `infrastructure/local/secrets/`. Certyfikat może zawierać metadane zakładu:
  nie publikuj go automatycznie w IPFS i publicznym API.

## Odbiór

Poprawne zgłoszenie musi mieć podpis z adresu aktywnego certyfikatu zgodnego z rejestrem on-chain.
Sprawdź: nieznany certyfikat, zły wystawca, wygaśnięcie, odwołanie (`revokeStation`),
inny adres, podpis niepasujący do klucza, poprawny certyfikat bez rejestracji
on-chain, `stageId` poza maską oraz niezgodność certyfikatu z rejestrem.
Podpisany certyfikat nie dowodzi poprawności pomiarów; służą temu QA,
współzatwierdzenie operatora i testy negatywne.

## Szczegółowy projekt formatu - propozycja DEC-13

Poniższy wariant rozwija wymaganie bez deklarowania, że format został
zatwierdzony. Nie ma jeszcze schema JSON ani kodu wystawcy.
Publiczny certyfikat składa się z `payload` i podpisu wystawcy; prywatny klucz
stacji nigdy nie jest polem certyfikatu.

| Pole payload | Proponowany typ / reguła | Znaczenie |
|---|---|---|
| schemaVersion | stała 1 | wersja formatu aplikacyjnego |
| certificateId | bytes32, unikalny w rejestrze wystawcy/deploymentu | niezmienny serial certyfikatu |
| stationId | bytes32, publiczny identyfikator techniczny | tożsamość stanowiska niezależna od jego klucza |
| stationAddress | adres EOA, niezerowy | adres uprawniony do podpisu raportów |
| issuerAddress | adres EOA ze zaufanej allowlisty | kto wystawił poświadczenie |
| chainId | dodatnia liczba sieci | zakres użycia certyfikatu |
| verifyingContract | adres wdrożonego SupplyChain | certyfikat nie przenosi prawa na inne wdrożenie |
| issuedAt | Unix seconds | początek ważności |
| expiresAt | Unix seconds, większe od issuedAt | koniec ważności, wyłączny |
| allowedStageIds | uporządkowana lista unikalnych numerów 0..3, niepusta | musi odpowiadać `stageMask` w rejestrze on-chain |

Proponowana walidacja czasu: `issuedAt <= serverNow < expiresAt`.
Clock skew nie powinien automatycznie przedłużać expiresAt; politykę czasu
i limity długości ważności zatwierdza DEC-11.
Adres w raporcie i certyfikacie ma porównywać tę samą wartość bajtową,
nie zależeć od checksum/case tekstu. JSON do hashowania ma jedną normalizację.

### Podpis certyfikatu oddzielny od podpisu raportu

Propozycja: JCS(payload) -> Keccak-256 -> `payloadHash` i EIP-712 wystawcy.
Domena: `name = SupplyChainStationRegistry`, `version = 1`, chainId
i verifyingContract z certyfikatu. Primary type:
`StationCertificate(bytes32 payloadHash)`.
Obwiednia zawiera payload i 65-bajtowy podpis jako hex.
To poświadczenie **off-chain**: nazwa Registry nie oznacza dodatkowego
wdrażanego kontraktu ani fizycznego rejestru.

Hash obejmuje cały payload, w tym zakres etapów, daty i adresy. Nie dodawaj
do niego później daty odebrania w backendzie ani podpisu jako własnego pola.
Backend sam wylicza hash i porównuje odzyskanego wystawcę do issuerAddress
oraz allowlisty zaufanej dla danego wdrożenia. Nie przyjmuje nowego wystawcy
tylko dlatego, że podpis jest matematycznie poprawny.
Oddzielne domeny/typy uniemożliwiają uznanie StageCompletion lub SIWE
za certyfikat. Dokładny wspólny wektor musi powstać przed implementacją.

### Rejestr, zakres i duplikaty

- certificateId w HTTP jest tym samym identyfikatorem co w payload.
- Rejestracja identycznego poświadczenia jest idempotentna.
- Ten sam certificateId z innym payload/podpisanym zamiarem to konflikt.
- Propozycja MVP: jeden aktywny certyfikat adresu stacji na deployment,
  wszystkie stare poświadczenia zachowane do audytu.
- Kolejne odnowienie ma nowy serial; nie zmienia dat i podpisu starego.
- Zakres etapów i ważność są egzekwowane przez kontrakt (`stageMask`, `expiresAt`
  w rejestrze). Zgodność certyfikatu z rejestrem sprawdza backend; rozbieżność
  oznacza stan ROLE_MISSING/niezgodny i blokuje nowe zgłoszenia.
- Danych organizacyjnych, prywatnego klucza i sekretów RPC nie ma w payload.

## Stan i synchronizacja tożsamości

| Stan lokalny | Znaczenie | Nowy raport |
|---|---|---|
| PENDING_ACTIVATION | zapisano poprawne poświadczenie, ale rejestracja on-chain niepotwierdzona | zablokowany |
| ACTIVE | poprawny certyfikat, zaufany wystawca, ważność i zgodna aktywna rejestracja on-chain | dopuszczony w ramach `stageMask` |
| EXPIRED | minął expiresAt | zablokowany, historia zostaje |
| REVOKED | `revokeStation` potwierdzone lub certyfikat jawnie odwołany | zablokowany |
| BLOCKED | kompromitacja adresu lub administracyjna blokada konta | zablokowany wraz z tokenami |
| REGISTRY_MISMATCH | certyfikat poprawny, ale rejestr on-chain nieaktywny lub różny, albo brak potwierdzenia | zablokowany; brak RPC to jawny błąd dostępności |

ACTIVE to wynik bieżącej weryfikacji, nie wieczne zaufanie do pola w bazie.
Do odbioru raportu i przed wysłaniem workera sprawdzaj ponownie certyfikat
i rejestr. Wygasła rejestracja podczas WAITING_FOR_QA nie pozwala na nowy send;
zapisz jawną przyczynę FAILED. Przy SUBMITTED najpierw uzgodnij wynik:
transakcja mogła już zostać wykonana przed wygaśnięciem lub odwołaniem.
Wygaśnięcie nie unieważnia historycznych potwierdzonych raportów.

Nie ma jednej transakcji obejmującej bazę i blockchain, ale źródłem egzekucji
jest tylko łańcuch:
- aktywacja jest po potwierdzeniu `StationRegistered`;
- reorg rejestracji przywraca brak gotowości do nowych zgłoszeń;
- odwołanie to jedna transakcja `revokeStation`; backend blokuje lokalnie od razu
  i uzgadnia już wysłane transakcje;
- nie ogłaszaj „klucz całkowicie unieważniony” przed potwierdzeniem `StationRevoked`.

### Rotacja i kompromitacja

Przy rutynowym odnowieniu zachowaj stationId, nadaj nowy certificateId
i określ daty (`registerStation` z nowym `expiresAt`). Przy rotacji klucza zmienia się
stationAddress: stary adres jest odwoływany, nowy rejestrowany. Nonce nie występuje.
Stare command pozostaje przypisane do starego adresu; zmiana certyfikatu
nie zmienia podpisu, deadline ani owner.

Przy kompromitacji: `revokeStation`, odwołanie tokenów, oznaczenie certyfikatu
i uzgodnienie nierozstrzygniętych tx są krokami z audytem. Stary podpis stacji
traci moc po swoim deadline (maksymalnie `maxDeadlineWindow`), a po
`revokeStation` kontrakt odrzuca go natychmiast; nie odblokowuj automatycznie
starego konta po wgraniu nowego certyfikatu.
Kompromitacja klucza wystawcy wymaga usunięcia go z allowlisty, przeglądu
wszystkich jego certyfikatów, blokady nowych wysyłek i bezpiecznego wystawienia
nowych poświadczeń. Historyczne dowody zachowaj wraz z informacją o incydencie.

## Odpowiedzialność i kryterium ukończenia

- Osoba 4: narzędzie generowania klucza, wystawianie certyfikatów (CLI), scenariusze.
- Osoba 1: rejestr stacji w kontrakcie, skrypty rejestracji/odwołania, weryfikacja
  certyfikatu w Javie i zgodności z rejestrem (T010), test braku przejścia po odwołaniu.
- Osoba 2: rejestr certyfikatów w bazie, walidacja przy przyjęciu zgłoszenia, audyt.
- Osoba 3: jasny stan „stacja nieaktywna” bez ujawniania poświadczeń publicznie.

Wspólny certyfikat testowy i przypadki mutacji każdego pola powstają razem
z wektorem podpisów (T008); nie wystarczy sprawdzenie obecności ID.
