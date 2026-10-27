# 3. Dwa klucze zamknięcia kontroli

## Wymaganie i wariant wykonawczy

Wymóg PDF: klucz stacji/czujnika + niezależny klucz operatora zmiany.
**Proponowany wariant DEC-04:** podpis EIP-712 stacji pod raportem i podpisana
transakcja operatora zatwierdzająca ten sam raport. Wariant pozostaje
do zatwierdzenia; nie oznacza portfela Safe ani dwóch podpisów jednej tx.

Etap wymagający tej bramki to **2 / QUALITY_CONTROL** z
[marszruty](production-rules.md). Każdy etap ma pozytywną ocenę stacji;
operator jest dodatkowym warunkiem etapu kontroli, nie zastępuje pomiarów.

## Cztery różne dowody

| Dowód | Kto podpisuje | Co autoryzuje | Gdzie weryfikowany |
|---|---|---|---|
| Login challenge | właściciel konta operatora/stacji | sesję dla wskazanego serwisu | backend; nie upoważnia do completeStage |
| StageCompletion EIP-712 | stacja | batchId, stageId, reportHash, qaPassed, nonce, deadline i domenę | backend + kontrakt |
| Transakcja approveStage | operator zmiany z QA_INSPECTOR_ROLE | trójkę batchId/stageId/reportHash w konkretnym kontrakcie i sieci | EVM + kontrakt |
| Transakcja completeStage | relayer | wykonanie wywołania z autoryzacją stacji | EVM + kontrakt; nie jest drugim podpisem QA |

Parametry StageCompletion i kolejność pól pozostają z [protocol](protocol.md).
Operator nie podpisuje nieznanego CID, nie podpisuje dowolnej decyzji dla
całej partii i nie posługuje się kluczem stacji.
Kod i adres kontraktu oraz chainId należą do kontekstu transakcji operatora.

## Klucz powiązania approval

`(chainId, contractAddress, batchId, stageId, reportHash)`.
W storage kontraktu sieć/adres wynikają z wdrożenia; nie powielaj ich
w każdym rekordzie, ale uwzględniaj w DB i klientach.
Zachowaj **adres operatora**, nie sam boolean.

Approval wiąże operatora z treścią raportu. **Nie obejmuje nonce/deadline
obwiedni podpisu stacji.** To oznacza, że w proponowanym MVP akceptacja
dotyczy raportu, a nie pojedynczej próby wysłania. Nowy hash wymaga nowego
approval; ponowne użycie tego samego raportu z inną autoryzacją stacji nie jest
dozwolone automatycznie, dopóki DEC-09 nie określi odcięcia poprzedniej.
Jeśli approval ma wygasać lub być związane z konkretną rewizją, trzeba dodać
takie pole do ABI/zdarzeń przed implementacją. Nie udawaj, że zapewnia to
sam deadline podpisu stacji.

## Przyjęcie i zatwierdzenie kandydata

1. Backend sprawdza podpis, certyfikat, profil pomiarów, qaPassed=true
   i tożsamość raportu. Trwale przyjmuje polecenie.
2. Worker pinując zapisuje CID i `WAITING_FOR_QA`; nie zajmuje nonce relayera.
3. Uprawniony operator czyta kandydata w chronionym API, łącznie z deadline
   i stanem aktualności. Publiczny paszport nie zawiera tej kolejki.
4. UI sprawdza schemat i hash raportu, pokazuje partię/etap, stację, pomiary,
   hash, sieć i kontrakt. Przed podpisem ponownie odświeża kandydata.
5. Operator swoim portfelem wykonuje proponowane
   `approveStage(batchId, stageId, reportHash)`.
6. Indexer po potwierdzeniach odczytuje approval; worker ponawia preflight.
7. Relayer wysyła completeStage z oryginalnym podpisem stacji.
8. Dopiero kanoniczny StageCompleted zamyka etap.

Szczegółowy diagram połączeń pozostaje w [flows](flows.md).
Receipt approval nie jest receipt completeStage; UI musi pokazywać dwa kroki:
„zatwierdzenie kontroli” i „rejestracja zakończenia etapu”.

## Warunki kontraktu

### approveStage

- Nadawca ma aktualną QA_INSPECTOR_ROLE.
- Partia istnieje, nie jest zakończona, oczekuje na etap 2.
- Parametry wskazują etap 2 i niezerowy reportHash.
- Zapisany adres operatora dotyczy dokładnie tej trójki; event pozwala
  odtworzyć autora i hash. Odróżniaj zatwierdzenie od ukończenia.

Kontrakt w tym prostym wywołaniu nie widzi JSON, certyfikatu ani podpisu stacji.
Nie może więc na etapie approveStage dowieść, że raport pochodzi z poprawnej
stacji. To sprawdzi completeStage. Backend/UI nie udostępniają do approval
niezweryfikowanego kandydata, ale kontrakt nie ufa wyłącznie interfejsowi.

### completeStage dla kontroli

- Wszystkie zwykłe warunki RBAC, partii, etapu, podpisu i nonce/deadline.
- Odzyskany signer stacji ma aktualną STATION_ROLE.
- qaPassed=true; hash w argumentach jest tym z podpisu stacji.
- Approval dla tej partii/etapu/hash istnieje, zapisany operator ma
  **aktualną** QA_INSPECTOR_ROLE i jego adres jest różny od signera stacji.
- Dopiero po tych sprawdzeniach aktualizacja stanu i zużycie nonce.

Różne adresy są weryfikowalnym minimum. Nie dowodzą dwóch różnych ludzi,
gdy ktoś posiada oba klucze. Organizacyjnie klucze powinny być rozdzielone.
Podpis admina/relayera bez wymaganej roli operatora nie spełnia kontroli.

## Zmiana decyzji, role i konkurencja

Propozycja dla prostego mappingu trójka -> operator:
- ponowne approve przez tego samego operatora jest idempotentne bez kolejnego
  efektu biznesowego;
- approval innego operatora nie nadpisuje aktywnego zatwierdzenia; zwraca jawny
  konflikt;
- jeśli zapisany operator utracił rolę QA, nowy uprawniony operator może
  zastąpić nieaktywne approval, z nowym eventem.

To reguły do zatwierdzenia wraz z DEC-04, nie istniejące ABI.
Nie dopisujemy cichego „ostatni zapis wygrywa”. Przy reorg indexer odtwarza
aktualny rekord. Cofnięcie roli przed completeStage blokuje poprzednie approval.
Ponowne nadanie roli może ponownie uaktywnić historyczne approval, jeżeli
kontrakt sprawdza tylko aktualną rolę. Wariant bazowy **nie zapewnia trwałego
unieważnienia** approval; jeśli jest potrzebne, należy zaprojektować
osobną operację revoke/version, a nie polegać na chwilowym odebraniu roli.

Brak osobnej formalnej odmowy QA i brak recovery wybranego w DEC-09:
odrzucony lub zmieniony kandydat nie może automatycznie zastąpić starego.

## Czas i awarie

- Deadline stacji ma obejmować pinowanie, inspekcję, approval i inclusion
  completeStage. Konkretna wartość to DEC-11, nie czas oczekiwania na 202.
- Po expiry przed send: FAILED; nie wydłużaj terminu w bazie.
- Po send: najpierw uzgodnij receipt; obecny czas nie dowodzi, że tx wygasła
  przed wykonaniem.
- Reorg approval: worker czeka lub wysłana tx rewertuje; cache nie daje prawa
  do przejścia wbrew kontraktowi.
- Odmowa portfela, brak gazu i zła sieć są jawne; UI nie pokazuje sukcesu.
- Unieważnienie certyfikatu w backendzie blokuje dalsze wysyłanie przez
  uczciwego relayera, lecz on-chain potrzebne jest także odebranie roli stacji.

## Minimalna macierz testów

| Przypadek | Oczekiwany wynik |
|---|---|
| Podpis stacji + approval niezależnego operatora, ten sam hash | jeden StageCompleted |
| Tylko stacja / tylko operator | brak przejścia |
| Dwa dowody z tego samego adresu | brak przejścia |
| Approval innego batchId, stageId lub reportHash | brak przejścia |
| qaPassed=false mimo approval | brak przejścia |
| Odebrana rola stacji/operatora przed zamknięciem | brak przejścia |
| Podmieniony pomiar po podpisie | odrzucenie hash/signature |
| Drugi approve tego samego operatora | brak drugiego efektu biznesowego |
| Approval ważnego operatora próbuje nadpisać drugi operator | konflikt według proponowanej polityki |
| Reorg approval lub completeStage | odtworzenie projekcji, bez fałszywego CONFIRMED |

Osoba 1 egzekwuje reguły w Solidity, osoba 2 prowadzi kandydatów i indeksację,
osoba 3 prezentuje dwa kroki w portfelu/UI, osoba 4 testuje brakujące dowody
i wyścigi. Zamknięcie DEC-04 wymaga wspólnego potwierdzenia tych zachowań.
