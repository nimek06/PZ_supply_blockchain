# Zakres i wymagania projektu

**Status:** wstępna specyfikacja do zatwierdzenia przez zespół, 2026-10-26.
Źródło nadrzędne: [opis PDF](../Opis_Projectu%20%283%29.pdf);
[mapa zgodności](source-alignment.md) wyjaśnia interpretację zakresu.
Nie przeprowadzono
inwentaryzacji działającej aplikacji: kod wykonawczy jeszcze nie istnieje.

## Zakres MVP

Jedna sekwencyjna linia produkcyjna, jeden kontrakt, jeden backend, jeden
relayer i czteroosobowy zespół. W zakresie jest 14 wymagań poniżej.
MVP obejmuje lokalną demonstrację i późniejsze wdrożenie testowe na Amoy.
Nie obejmuje produkcyjnego SLA, płatności, handlu tokenami, NFT, wielu zakładów,
równoległych gałęzi technologicznych, proxy kontraktu ani Kubernetes.
PWA/offline i powiadomienia push są rozszerzeniami, nie zależnościami MVP.
Nie przebudowujemy fizycznej linii i nie wymagamy PLC. Kontekst pełnego
łańcucha dostaw nie rozszerza MVP na logistykę dystrybucji.
Plan zakłada 12 tygodni i backend/kontrakty w pierwszych 4-5 tygodniach.

## Aktorzy i scenariusze

### US1 - Przygotowanie partii i linii, P1

Administrator rejestruje certyfikowane stacje i operatorów zmiany; operator zakłada partię.
Mając skonfigurowane role, operator otrzymuje identyfikator polecenia,
a po potwierdzeniu widzi partię gotową do pierwszego etapu.
Osoba bez uprawnień nie może wykonać tej operacji.

**Odbiór:** jedno poprawne utworzenie i jedno odrzucone żądanie bez uprawnień;
ponowienie tego samego polecenia nie tworzy drugiej partii.

### US2 - Rejestracja etapu przez stację, P1

Stacja podpisuje pomiary dla konkretnej partii i etapu.
System przyjmuje polecenie, archiwizuje raport i rejestruje etap po sprawdzeniu
kolejności oraz uprawnień. Operator widzi stan oczekiwania, a następnie wynik.

**Odbiór:** pozytywny raport przesuwa partię dokładnie raz; podmiana treści,
powtórzenie zużytego podpisu i etap poza kolejnością nie przesuwają jej.

### US3 - Współzatwierdzenie kontroli przez operatora zmiany, P1

Operator zmiany z uprawnieniem QA ogląda raport podpisany przez stację
i swoim kluczem zatwierdza konkretny skrót.
Bez wymaganego zatwierdzenia linia nie przechodzi przez bramkę QA.
Negatywnego wyniku stacji nie można obejść zatwierdzeniem inspektora.

**Odbiór:** porównanie przepływu bez zatwierdzenia, z nim i z innym reportHash.
Źródło raportów oczekujących opisuje [flows](flows.md).

### US4 - Publiczna weryfikacja produktu, P2

Użytkownik bez portfela otwiera paszport z identyfikatora lub QR, czyta historię
i weryfikuje integralność raportu względem potwierdzonego zdarzenia.

**Odbiór:** poprawny raport jest zgodny, podmieniony powoduje jawny błąd,
a brak partii nie jest prezentowany jako pusta, poprawna historia.

### US5 - Wznowienie po awarii, P1

Obsługa odczytuje status polecenia po timeout lub restarcie.
System uzgadnia wcześniejsze wyniki i nie wysyła nowej operacji w ciemno.

**Odbiór:** restart po pinowaniu i po wysłaniu nie gubi zadania ani nie powiela
ukończonego etapu; reorganizacja usuwa niekanoniczny wpis z historii.

## Wymagania

| ID | Wymaganie i oczekiwany wynik | Scenariusz |
|---|---|---|
| REQ-001 | Inicjalizacja środowiska i nadanie ról pozwalają odtworzyć jedną linię od pustej konfiguracji; role są egzekwowane. | US1 |
| REQ-002 | Uprawniony operator tworzy partię idempotentnie; odczyt pokazuje ją dopiero po uzgodnionym potwierdzeniu. | US1 |
| REQ-003 | Każdy raport ma wspólny, ścisły format, jednostki i wersję; ten sam raport ma identyczny skrót u wszystkich klientów. | US2 |
| REQ-004 | Zgłoszenie jest związane z podpisem uprawnionej stacji, partią, etapem, wynikiem QA, nonce, terminem i konkretnym wdrożeniem; replay nie przesuwa linii. | US2 |
| REQ-005 | Produkcja przechodzi wyłącznie w zatwierdzonej kolejności; negatywne pomiary lub brak ręcznego QA blokują przejście. | US2, US3 |
| REQ-006 | Przyjęte polecenie jest trwałe, ma status i zachowuje tożsamość przy retry; równoległe stacje nie powodują kolizji relayera. | US2, US5 |
| REQ-007 | Potwierdzony etap wskazuje przechowany raport i jego skrót; niedostępność lub podmiana raportu jest odróżniona od zgodności. | US2, US4 |
| REQ-008 | Historia jest uporządkowana, paginowana i odbudowywalna z kanonicznych zdarzeń; reorg nie pozostawia fałszywego potwierdzenia. | US4, US5 |
| REQ-009 | Operator widzi loading, brak danych, oczekiwanie, sukces i błąd; QA może przeglądać i zatwierdzać właściwe kandydatury raportów. | US1, US3 |
| REQ-010 | Publiczny paszport i QR działają bez portfela i nie ujawniają danych wewnętrznych ani sekretów. | US4 |
| REQ-011 | Symulator uruchamia odtwarzalne scenariusze automatyczne i interaktywne, w tym QA fail, zły etap i bezpieczne ponowienie. | US2, US5 |
| REQ-012 | Restart, timeout i trwałe odrzucenie mają jawny wynik i procedurę obsługi; zapis/statusy administracyjne są chronione i diagnozowalne bez ujawniania sekretów. | US1, US5 |
| REQ-013 | Każda symulowana stacja ma własną parę kluczy i certyfikat; nieznany, wygasły, odwołany lub niezgodny z podpisem certyfikat nie autoryzuje zgłoszenia. | US1, US2 |
| REQ-014 | Zamknięcie etapu kontroli wymaga dwóch niezależnych kluczy: stacji/czujnika oraz operatora zmiany; oba dowody dotyczą tej samej partii, etapu i raportu. | US3 |

## Kluczowe pojęcia

- **Partia:** produkt/zbiór przechodzący przez kolejne etapy.
- **Raport:** niezmienne pomiary konkretnej stacji dla etapu.
- **Polecenie:** trwały zamiar wykonania operacji, nie dowód jej wykonania.
- **Próba transakcji:** konkretna podpisana transakcja relayera.
- **Zatwierdzenie QA:** decyzja dotycząca partii, etapu i skrótu raportu.
- **Zdarzenie:** kanoniczny fakt z kontraktu, z pozycją w łańcuchu.
- **Projekcja:** odtwarzalny widok zdarzeń dla API.

## Kryteria sukcesu demonstracji

1. Cała uzgodniona sekwencja jednej partii jest widoczna w publicznym paszporcie.
2. 10 ponowień tego samego zgłoszenia daje jeden efekt produkcyjny.
3. Dwa równoległe zgłoszenia różnych stacji otrzymują jednoznaczne wyniki
   bez zgubionego polecenia i bez kolizji transakcji konta wysyłającego.
4. Każdy negatywny scenariusz z US2/US3 pozostawia etap nieukończony.
5. Zatrzymanie procesu w dwóch wyznaczonych punktach US5 pozwala wznowić
   pracę bez ręcznego poprawiania danych.
6. W 100% przykładów odczytu zmiana choć jednego podpisanego pomiaru jest wykryta.
7. Brak któregokolwiek z dwóch wymaganych kluczy blokuje zamknięcie kontroli;
   drugi podpis od tego samego adresu nie spełnia wymogu niezależności.
8. Każda aktywna stacja demonstracyjna ma zweryfikowany certyfikat.

Nie deklarujemy czasu potwierdzenia publicznej sieci ani liczby użytkowników
bez pomiarów. Cele wydajności i limity produkcyjne są decyzją przed wdrożeniem
innym niż demonstracyjne.

## Założenia i gotowość

Użytkownik wybrał ogólne przygotowanie -> montaż -> kontrola -> pakowanie,
bez produktu ani norm fizycznych. Rozwinięcie wymagań REQ-002..005/014:
[reguły produkcji](production-rules.md), [poprawki](corrections.md)
i [dwa klucze](quality-approval.md). REQ-012/013 rozwijają
[uprawnienia](access-control.md) i [certyfikaty](station-identity.md).
Mechanizm recovery pozostaje niewybrany zgodnie z odpowiedzią użytkownika;
nie oznacza to ukończenia kryteriów pełnego wznowienia po trwałym błędzie.

Format raportu i wariant podpisu pochodzą z [protokołu](protocol.md).
Pozostałe propozycje oraz ich właścicieli zawiera [decisions](decisions.md).
Ten dokument nadaje identyfikatory wymaganiom, ale **nie zatwierdza**
nieustalonych procesów fabrycznych. Plan może powstać wstępnie;
implementacja danej części wymaga zamknięcia przypisanej decyzji.
