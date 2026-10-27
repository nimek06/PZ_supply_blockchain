# 2. Nieudana kontrola, poprawki i ponowienia

**Decyzja użytkownika 2026-10-27:** opisać alternatywy, **nie wybierać**
mechanizmu odzyskania po trwałym błędzie lub wygaśnięciu podpisu.
DEC-09 pozostaje otwarta. Żaden endpoint ani funkcja unieważniania nonce
nie jest przez ten dokument zatwierdzona do implementacji.

## Rozróżnienie czterech sytuacji

| Sytuacja | Tożsamość operacji | Co wolno zrobić |
|---|---|---|
| Timeout HTTP lub restart procesu | ten sam raport, digest, nonce, deadline | odczytać status / ponowić identyczne żądanie |
| Przejściowy błąd Pinaty/RPC | to samo przyjęte polecenie | wznowić właściwy krok z zapisanych danych |
| Nowy pomiar, inna treść lub przedłużony deadline | **nowa autoryzacja**, nawet dla tej samej partii | wymaga decyzji DEC-09; nie podmieniać body starego command |
| Ukończony etap on-chain | historyczny fakt | odczyt; naprawa procesu wymaga jawnego nowego modelu, nie cofania bazy |

Idempotencja chroni przed duplikatem, a nie daje prawa do zmiany podpisanej
treści. Podpisy nie są edytowalne. CID może być wynikiem ponownego pinowania
tej samej treści, ale reportHash musi pozostać zgodny.

## Macierz wyniku i dalszego działania

| Zdarzenie | Skutek | Działanie klienta/obsługi |
|---|---|---|
| Zły JSON, brak pola, inny batchId | 400, brak nowego przyjętego command | poprawić dane i ponownie podpisać; nie zakładać, że trafiły do chaina |
| Zły podpis/brak roli/certyfikat nieważny | 403; dla wymaganego logowania brak sesji daje 401 | przywrócić legalną tożsamość; nie retry w pętli |
| Zły etap lub konflikt nonce/idempotencji | 409 | odczytać stan i istniejące polecenie |
| qaPassed=false albo pomiary niespełniające profilu | 422, brak StageCompleted i brak zużycia nonce on-chain | nowy pomiar dopiero po ustaleniu, czy poprzedni podpis mógł już wyjść poza stację |
| Brak decyzji operatora | WAITING_FOR_QA | czekać do decyzji/expiry; nie wyświetlać completed |
| Operator odmawia w portfelu | brak transakcji approval | kandydat nadal oczekuje; odmowa portfela to nie zapis „QA odrzucone” |
| Deadline minął przed przygotowaniem send | FAILED z powodem | nie wydłużać deadline w rekordzie; potrzebna nowa podpisana autoryzacja i DEC-09 |
| Zapisana transakcja, wynik send nieznany | SUBMITTED, wynik nierozstrzygnięty | uzgadniać hash/nonce/receipt; nie tworzyć kolejnego polecenia |
| Kanoniczny receipt revert | FAILED; nonce relayera zużyty, nonce stacji niezużyty | ustalić przyczynę i zagrożenie starym podpisem; nie usuwać rekordu |
| Kanoniczny StageCompleted | CONFIRMED po polityce potwierdzeń | kolejny etap; powtórzenie wskazuje poprzedni wynik |
| Reorg usuwa zdarzenie | poprzedni dowód przestaje być aktualny | odbudować projekcję i uzgodnić transakcję; brak automatycznego nowego podpisu |

Odrzucenie przez backend nie unieważnia kryptograficznie podpisu.
Nawet jeśli nie utworzono command, podpis mógł zostać udostępniony innemu
relayerowi. Poprawa z tym samym nonce wymaga oceny ważności starego dowodu.

## Alternatywy recovery - bez wybranego wariantu

### A. Jawne unieważnienie nonce na łańcuchu

Stacja autoryzuje zmianę swojego oczekiwanego nonce. Operacja jest podpisana
osobno i związana z siecią/kontraktem; może być bezpośrednią transakcją stacji
albo osobnym podpisem przekazywanym relayerowi. Po potwierdzeniu stary podpis
nie może już zostać użyty, a nowy raport dostaje aktualny nonce.

**Zalety:** wyraźne odcięcie starej autoryzacji; zachowana unikalność
`deployment + station + nonce`. **Koszt:** dodatkowe ABI, event, gaz,
uprawnienia, UI/CLI i obsługa wyścigu z pierwotną transakcją.

Jeśli pierwotne completeStage wygra wyścig, nie wolno anulować jego skutku
przez skasowanie projekcji. Unieważnienie musi sprawdzać oczekiwany nonce.
Szczegóły increment/jump i finansowanie gazu są częścią decyzji, nie ustalone.

### B. Wersjonowane autoryzacje / jawne zastępowanie zamiaru

Projekt podpisu i kontraktu rozróżnia rewizję autoryzacji oraz on-chain
unieważnienie poprzedniej wersji. Backend zachowuje wszystkie rewizje,
związki zastąpienia i wyniki prób.

**Zalety:** czytelny model zmian raportu, potencjalnie lepszy UX.
**Koszt:** zmiana EIP-712, modelu danych, idempotencji, approvals i reguł
kontraktu. Sama kolumna `revision` w PostgreSQL **nie wystarczy**:
stary podpis trzeba również odciąć na łańcuchu.

Ta alternatywa nie jest zgodna bez zmian z obecną prostą unikalnością
`station + nonce`. Nie dodawaj nowej wersji tylko w HTTP.

### C. Nowy podpis dopiero po wygaśnięciu starego

Rozważalny tylko gdy uzgodniono kanoniczny wynik poprzedniej transakcji,
stary deadline minął także według łańcucha, a polityka finalności i brak
nierozstrzygniętych prób wykluczają oczekujące wykonanie.
Potrzebne jest zachowanie osobnego rekordu poprzedniej autoryzacji i historii
approval; nie nadpisanie istniejącego polecenia.

**Zalety:** możliwy brak dodatkowej funkcji unieważniania.
**Koszt:** czekanie, trudne granice reorg i zmiana modelu lokalnej idempotencji
dla ponownego użycia nonce. To **nie jest gotowy, bezpieczny fallback**.
Bez pełnego projektu nie implementujemy tej alternatywy.

### D. Ręczne zatrzymanie i diagnoza, bez ponownego podpisu

Bezpieczne ograniczenie prototypu do czasu wyboru A/B/C: status pokazuje błąd,
obsługa uzgadnia wynik, ale nie istnieje przycisk „ponów z nowym podpisem”.
Nowe odizolowane środowisko testowe może służyć następnemu scenariuszowi,
lecz reset bazy/chaina nie jest naprawą produkcyjnego polecenia.

**Zalety:** brak pozornej gwarancji recovery. **Ograniczenie:** nie spełnia
pełnego odbioru wznowienia po trwałym błędzie; T026 pozostaje zablokowane.

## Czego nie robić niezależnie od wariantu

- Nie usuwać constraints ani rekordu idempotencji, aby „odblokować stację”.
- Nie zwiększać nonce stacji w bazie bez zgodnej zmiany stanu kontraktu.
- Nie przełączać FAILED na ACCEPTED ze zmienionym raportem/podpisem.
- Nie zmieniać qaPassed=false na true decyzją człowieka.
- Nie uznawać timeoutu lub braku receipt za dowód braku transakcji.
- Nie mieszać replacement transakcji relayera (ten sam zamiar/nonce EVM)
  z nowym raportem stacji (inna autoryzacja aplikacyjna).

## Odmowa i korekta raportu QA

W obecnym MVP brak osobnej operacji on-chain „QA odrzuciło raport”.
Brak approval blokuje przejście. Jeżeli ma istnieć formalna odmowa, trzeba
ustalić jej podpisany zakres, zdarzenie, finalność, widoczność i możliwość
ponownego zgłoszenia. Sam klik „odrzuć” w przeglądarce nie zmienia kontraktu.

Podmiana raportu zmienia hash i wymaga nowego approval, ale stary approval
nie znika tylko dlatego, że zmienił się ekran UI. Nie wolno równolegle wysyłać
starej i nowej autoryzacji. Powiązania opisuje [quality-approval](quality-approval.md).

## Przed zamknięciem DEC-09

Osoby 1, 2 i 4 muszą wybrać A/B/C lub zapisać ograniczenie D; opisać
wyścig ze starym send, odcięcie podpisu, wpływ na approval i odwołanie roli,
zmiany ABI/DB/CLI oraz test restartu/reorg. Dopiero wtedy uzupełnić
protokół, schematy, T026 i kryteria odbioru. Ten dokument nie wybiera za zespół.
