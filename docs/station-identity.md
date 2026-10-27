# Tożsamość i certyfikat symulowanej stacji

## Wymaganie źródłowe

PDF wymaga własnej pary kluczy i certyfikatu dla każdej stacji w symulacji
(s. 2), oraz podpisów stacji i operatora przy zamknięciu kontroli (s. 3).
Nie wymaga instalacji TPM, sterownika PLC, pełnej infrastruktury PKI ani
certyfikacji sprzętu. Nie deklaruj rzeczywistej atestacji sprzętowej.

## Proponowany wariant MVP - DEC-13

Podpisane przez administratora poświadczenie stacji w JSON, przechowywane
off-chain. To **certyfikat aplikacyjny**, nie certyfikat X.509 do TLS.
Jeżeli prowadzący wymaga X.509/mTLS, zatwierdź zmianę przed implementacją.

Proponowane pola: wersja, stationId, publiczny adres EVM stacji, identyfikator
wystawcy, serial, issuedAt, expiresAt oraz dozwolona linia/zakres stanowiska.
Wystawca podpisuje poświadczenie osobnym, udokumentowanym typem danych.
Schemat i algorytm muszą być określone przed generowaniem certyfikatów;
nie weryfikuj certyfikatu jako StageCompletion.
Scope certyfikatu nie daje automatycznie uprawnień, których kontrakt nie sprawdza.

## Cykl życia

1. Narzędzie lokalne generuje klucz stacji, bez wypisywania sekretu do logów.
2. Administrator sprawdza adres/stanowisko i wystawia certyfikat.
3. Admin nadaje temu adresowi `STATION_ROLE` w kontrakcie.
4. Backend rejestruje certyfikat i weryfikuje wystawcę, ważność, unikalność
   serialu, status unieważnienia i zgodność adresu z odzyskanym signerem raportu.
5. Stacja podpisuje raport swoim kluczem. W żądaniu podaje `stationCertificateId`
   poza raportem objętym JCS; backend wybiera zapisany, zweryfikowany certyfikat.
6. Kontrakt niezależnie weryfikuje podpis EIP-712 i aktualne role.
7. Przy unieważnieniu admin usuwa rolę on-chain i blokuje certyfikat w backendzie.
   Sama blokada w backendzie nie powstrzyma użycia skradzionego klucza przez
   innego uprawnionego relayera: odwołanie roli jest konieczne.
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

Poprawne zgłoszenie musi mieć podpis z adresu aktywnego certyfikatu oraz rolę.
Sprawdź: nieznany certyfikat, zły wystawca, wygaśnięcie, odwołanie, inny adres,
podpis niepasujący do klucza i poprawny certyfikat bez STATION_ROLE.
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
| allowedStageIds | uporządkowana lista unikalnych numerów 0..3, niepusta | zawężenie zakresu w backendzie |

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
- Scope etapów jest kontrolą backendu. Kontrakt sprawdza STATION_ROLE,
  a nie poświadczenie; złośliwy relayer może obejść samo zawężenie scope.
  Jeśli to niedopuszczalne, potrzebna jest dodatkowa reguła on-chain.
- Danych organizacyjnych, prywatnego klucza i sekretów RPC nie ma w payload.

## Stan i synchronizacja tożsamości

| Stan lokalny | Znaczenie | Nowy raport |
|---|---|---|
| PENDING_ACTIVATION | zapisano poprawne poświadczenie, ale nie potwierdzono roli/stanu | zablokowany |
| ACTIVE | poprawny certyfikat, zaufany wystawca, ważność i aktualna STATION_ROLE | dopuszczony w ramach scope |
| EXPIRED | minął expiresAt | zablokowany, historia zostaje |
| REVOKED | jawnie odwołano certyfikat | zablokowany |
| BLOCKED | kompromitacja adresu lub administracyjna blokada konta | zablokowany wraz z sesjami |
| ROLE_MISSING | certyfikat poprawny, ale brak roli lub brak potwierdzenia jej aktualności | zablokowany; brak RPC to jawny błąd dostępności |

ACTIVE to wynik bieżącej weryfikacji, nie wieczne zaufanie do pola w bazie.
Do odbioru raportu i przed wysłaniem workera sprawdzaj ponownie certyfikat
i rolę. Wygasły certyfikat podczas WAITING_FOR_QA nie pozwala na nowy send;
zapisz jawną przyczynę FAILED. Przy SUBMITTED najpierw uzgodnij wynik:
transakcja mogła już zostać wykonana, a kontrakt nie zna expiresAt certyfikatu.
Wygaśnięcie nie unieważnia historycznych potwierdzonych raportów.

Nie ma jednej transakcji obejmującej rejestr i blockchain:
- aktywacja jest dopiero po obu potwierdzonych warunkach;
- reorg nadania roli przywraca brak gotowości do nowych zgłoszeń;
- odwołanie zaczyna się lokalną blokadą i zatrzymaniem nowych wysyłek,
  a następnie odebraniem roli on-chain i uzgodnieniem już wysłanych transakcji;
- nie ogłaszaj „klucz całkowicie unieważniony” przed potwierdzeniem odebrania
  roli; uczciwy backend może blokować wcześniej, ale kontrakt nadal jej udziela.

### Rotacja i kompromitacja

Przy rutynowym odnowieniu zachowaj stationId, nadaj nowy certificateId
i określ daty. Przy rotacji klucza zmienia się stationAddress: nowy adres
ma własny licznik nonce na kontrakcie. Nie kopiuj nonce starego konta.
Stare command pozostaje przypisane do starego adresu; zmiana certyfikatu
nie zmienia podpisu, deadline ani owner.

Przy kompromitacji nie wystarczy nowe poświadczenie: blokada adresu,
logout sesji, odwołanie certyfikatu, odebranie roli oraz uzgodnienie
nierozstrzygniętych tx są osobnymi krokami z audytem. Stare podpisy
i ewentualne ponowne nadanie roli wymagają oceny DEC-09; nie odblokowuj
automatycznie starego konta po wgraniu nowego certyfikatu.
Kompromitacja klucza wystawcy wymaga usunięcia go z allowlisty, przeglądu
wszystkich jego certyfikatów, blokady nowych wysyłek i bezpiecznego wystawienia
nowych poświadczeń. Historyczne dowody zachowaj wraz z informacją o incydencie.

## Odpowiedzialność i kryterium ukończenia

- Osoba 4: narzędzie generowania klucza, wczytywanie certyfikatu i scenariusze.
- Osoba 2: rejestr, wystawca/allowlista, walidacja, odwołanie i audyt.
- Osoba 1: nadawanie/odebranie roli i test braku przejścia po odwołaniu.
- Osoba 3: jasny stan „stacja nieaktywna” bez ujawniania poświadczeń publicznie.

Przed zamknięciem DEC-13 zatwierdź format, algorytm, miejsce klucza wystawcy,
okres ważności i proces rotacji/awarii. Przygotuj wspólny certyfikat testowy
i przypadki mutacji każdego pola, nie tylko sprawdzenie obecności ID.
