# 2. Nieudana kontrola, poprawki i ponowienia

**Decyzja 2026-10-27 (DEC-09, V2-02):** wybrano uproszczony wariant C.
Nie ma nonce stacji ani funkcji unieważniania; jedyną ochroną przed
ponownym użyciem są: jedno ukończenie na `(batchId, stageId)`, deadline
(z limitem okna w kontrakcie) i domena EIP-712. Poprzednie alternatywy A
(unieważnienie nonce), B (wersjonowane autoryzacje) i D (tylko diagnoza)
nie są implementowane.

## Rozróżnienie czterech sytuacji

| Sytuacja | Tożsamość operacji | Co wolno zrobić |
|---|---|---|
| Timeout HTTP lub restart procesu | ten sam raport i digest | odczytać status / ponowić identyczne żądanie |
| Przejściowy błąd Pinaty/RPC | to samo przyjęte polecenie | wznowić właściwy krok z zapisanych danych |
| Nowy pomiar, inna treść lub nowy deadline | **nowa autoryzacja** tego samego etapu | wolno według reguły zastępowania poniżej |
| Ukończony etap on-chain | historyczny fakt | odczyt; brak cofania |

Idempotencja chroni przed duplikatem, nie daje prawa do zmiany podpisanej treści.
Podpisów się nie edytuje. CID może wynikać z ponownego pinowania tej samej treści,
ale `reportHash` pozostaje zgodny.

## Reguła zastępowania raportu

Dla `(deployment, batchId, stageId)`:

| Stan istniejącego polecenia | Nowe zgłoszenie z innym `reportHash` |
|---|---|
| identyczny digest | istniejące `commandId` |
| aktywne (ACCEPTED, PINNING, WAITING_FOR_QA, READY_TO_SUBMIT, SUBMITTED) | `409 STAGE_ALREADY_PENDING` |
| CONFIRMED, `outcome=ACCEPTED` | `409` (etap ukończony) |
| CONFIRMED, `outcome=REJECTED` | dozwolone (odrzucenie nie kończy etapu) |
| FAILED | dozwolone gdy potwierdzony blok ma `timestamp` większy od starego `deadline` i nie ma nierozstrzygniętych prób |

Uzasadnienie: po upływie deadline kontrakt odrzuca stary podpis, więc nie da się go
już wykonać nawet po późnym dołączeniu transakcji do bloku. Warunek „potwierdzony
blok” (polityka potwierdzeń DEC-07) chroni przed płytkim reorg.
Okno oczekiwania jest ograniczone przez `maxDeadlineWindow` (robocze 3600 s).
Podpis z `qaPassed=false` nie przesuwa partii, więc jego zastąpienie jest natychmiastowe.
Stare polecenie, jego approval i próby pozostają w bazie do audytu; nie są nadpisywane.

## Macierz wyniku i dalszego działania

| Zdarzenie | Skutek | Działanie klienta/obsługi |
|---|---|---|
| Zły JSON, brak pola, inny batchId | 400, brak polecenia | poprawić dane i ponownie podpisać |
| Zły podpis / stacja nieaktywna, wygasła lub poza zakresem | 403; brak sesji do chronionych odczytów daje 401 | przywrócić legalną tożsamość; bez retry w pętli |
| Stage poza kolejnością, aktywne polecenie lub konflikt | 409 | odczytać stan i istniejące polecenie |
| Raport niespójny z profilem pomiarów (`qaPassed` sprzeczne z oceną) | 422 | popraw raport i podpisz ponownie |
| `qaPassed=false` zgodne z profilem | 202, polecenie kończy `CONFIRMED` z `outcome=REJECTED` po `StageRejected` | nowy raport tego etapu wolno zgłosić od razu |
| Brak approval operatora | WAITING_FOR_QA | czekać do approval albo wygaśnięcia |
| Operator odmawia (brak podpisu) | kandydat czeka | po deadline FAILED; nowy raport po warunku reguły |
| Deadline minął przed send | FAILED z powodem | nowy raport podpisany od nowa po warunku reguły |
| Zapisana transakcja, wynik send nieznany | SUBMITTED | uzgadniać hash/receipt; bez nowego polecenia |
| Receipt revert | FAILED | ustalić przyczynę; nowy raport po warunku reguły |
| Kanoniczny StageCompleted | CONFIRMED (`ACCEPTED`) po polityce potwierdzeń | następny etap |
| Reorg usuwa zdarzenie | dowód przestaje być aktualny | indexer odtwarza okno; status wraca do SUBMITTED i jest uzgadniany |

Odrzucenie przez backend nie unieważnia kryptograficznie podpisu: podpis mógł
trafić do innego relayera. Dlatego deadline podpisu jest krótkie, a kontrakt
ogranicza jego maksimum; nowy raport tego samego etapu nie jest akceptowany,
dopóki stary może się jeszcze wykonać.

## Procedura wznowienia (CLI symulatora i API)

1. `status` polecenia pokazuje `FAILED` lub `AWAITING_DEADLINE` z czasem, od którego
   nowy raport jest dozwolony (pole `retryAllowedAfter`).
2. Po tym czasie stacja buduje nowy raport (nowy `timestamp`, ewentualnie nowe
   pomiary), podpisuje `StageCompletion` z nowym deadline i wysyła `POST stages`.
3. Backend stosuje regułę zastępowania; stare polecenie zostaje jako FAILED.
4. Dla etapu 2 operator podpisuje nowy `StageApproval` dla nowego hasha.

## Czego nie robić

- Nie usuwać constraints ani rekordu idempotencji, aby „odblokować stację”.
- Nie zmieniać FAILED na ACCEPTED ze zmienionym raportem/podpisem.
- Nie zmieniać `qaPassed=false` na `true` decyzją człowieka.
- Nie uznawać timeoutu lub braku receipt za dowód braku transakcji.
- Nie wysyłać równolegle starej i nowej autoryzacji tego samego etapu.
- Nie traktować transakcji relayera jako nowej autoryzacji stacji.

## Odmowa i korekta raportu QA

W MVP brak formalnej operacji „QA odrzuciło raport”. Brak approval blokuje przejście
i po deadline kończy polecenie jako FAILED. Gdy stacja zgłosi wynik negatywny,
zapis odrzucenia jest `StageRejected` (patrz wyżej). Osobne podpisane odrzucenie
operatora wymagałoby nowego typu EIP-712 i zdarzenia; to rozszerzenie poza MVP.

## Testy

Wygaśnięcie i ponowienie po deadline, konflikt aktywnego polecenia, zastąpienie po
`StageRejected`, restart między krokami, wyścig starego `completeStage` z nowym
zgłoszeniem po deadline oraz stary podpis po deadline (revert w kontrakcie).
Odpowiedzialni: osoba 2 (reguła w backendzie), 1 (revert po deadline w kontrakcie),
4 (CLI i scenariusze).
