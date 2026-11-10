# 3. Dwa klucze zamknięcia kontroli

## Wymaganie i rozwiązanie (DEC-04, zmiana V2-03)

Wymóg PDF: klucz stacji/czujnika + niezależny klucz operatora zmiany.
**Rozwiązanie:** podpis EIP-712 stacji pod raportem (`StageCompletion`) i podpis
EIP-712 operatora zmiany pod tym samym raportem (`StageApproval`), oba weryfikowane
razem przez kontrakt w jednym wywołaniu `completeStage`. Nie ma transakcji
`approveStage`, stanu approval w storage ani portfela multisig (Safe).
Operator nie płaci gazu; transakcję wysyła relayer.

Etap wymagający bramki to **2 / QUALITY_CONTROL** z [marszruty](production-rules.md).
Każdy etap ma pozytywną ocenę stacji; operator jest dodatkowym warunkiem etapu
kontroli, nie zastępuje pomiarów.

## Cztery różne dowody

| Dowód | Kto podpisuje | Co autoryzuje | Gdzie weryfikowany |
|---|---|---|---|
| Login (SIWE) | właściciel konta operatora/stacji | token Bearer dla backendu | backend; nie upoważnia do completeStage |
| StageCompletion EIP-712 | stacja | batchId, stageId, reportHash, qaPassed, deadline i domenę | backend + kontrakt |
| StageApproval EIP-712 | operator zmiany z QA_INSPECTOR_ROLE | batchId, stageId, reportHash, approvalDeadline i domenę | backend + kontrakt |
| Transakcja completeStage | relayer | wykonanie wywołania z dwoma dowodami | EVM; nie jest drugim podpisem QA |

Typy i kolejność pól: [protokół](protocol.md). Operator nie podpisuje CID, decyzji
dla całej partii ani niczego kluczem stacji. Domena (chainId, adres kontraktu)
chroni przed użyciem podpisu w innym wdrożeniu.

## Wiązanie approval

`StageApproval(batchId, stageId, reportHash, approvalDeadline)` w domenie
konkretnego kontraktu i sieci. Approval dotyczy **raportu** (hash), a nie
pojedynczej próby wysyłki; wygasa po własnym `approvalDeadline`.
Nowy raport (nowy hash) wymaga nowego approval. Stary approval nie może
uprawnić innego raportu, bo hash jest częścią podpisu.
Podpis jest argumentem wywołania, więc pozostaje w calldata i jest audytowalny;
kontrakt nie przechowuje go w storage. Adres operatora trafia do zdarzenia
`StageCompleted.qaOperator`.

## Przyjęcie i zatwierdzenie kandydata

1. Backend sprawdza podpis, certyfikat, rejestr stacji, profil pomiarów,
   `qaPassed=true` i tożsamość raportu. Trwale przyjmuje polecenie.
2. Worker pinując zapisuje CID i `WAITING_FOR_QA`.
3. Uprawniony operator czyta kandydata w chronionym API (deadline i aktualność).
   Publiczny paszport tej kolejki nie zawiera.
4. UI sprawdza schemat i hash raportu, pokazuje partię/etap, stację, pomiary,
   hash, sieć i kontrakt. Przed podpisem odświeża kandydata.
5. Operator podpisuje portfelem `StageApproval` (`eth_signTypedData_v4`)
   i wysyła go: `POST /api/v1/qa/candidates/{commandId}/approval`.
6. Backend odzyskuje adres z podpisu, wymaga zgodności z sesją, aktualnej
   QA_INSPECTOR_ROLE (odczyt z łańcucha), adresu różnego od stacji i
   niewygasłego `approvalDeadline`. Zapisuje approval w `qa_approvals`.
7. Polecenie przechodzi do `READY_TO_SUBMIT`; worker ponawia preflight i wysyła
   `completeStage` z obu podpisami.
8. Dopiero kanoniczny `StageCompleted` zamyka etap.

Szczegółowy diagram: [flows](flows.md). UI pokazuje dwa kroki: „zatwierdzenie
kontroli” (podpis, bez transakcji) i „rejestracja zakończenia etapu” (relayer).

## Warunki kontraktu

`completeStage` dla etapu 2 z `qaPassed=true` wymaga:

- zwykłych warunków: RELAYER_ROLE nadawcy, istniejąca niezakończona partia,
  `stageId == nextStage`, deadline w oknie, aktywna stacja z `stageMask`;
- poprawnego `approvalSignature` dla `StageApproval` z tymi samymi
  `batchId`, `stageId`, `reportHash`;
- `block.timestamp <= approvalDeadline`;
- operatora z **aktualną** QA_INSPECTOR_ROLE i adresem **różnym** od adresu stacji.

Kontrakt nie widzi JSON ani certyfikatu; zgodność hasha z treścią raportu
sprawdzają backend, UI operatora i paszport. Brak lub błąd approval cofa całe
wywołanie (revert), bez zmiany stanu.

Różne adresy są weryfikowalnym minimum. Nie dowodzą dwóch różnych ludzi, gdy ktoś
posiada oba klucze; organizacyjnie klucze rozdziel. Podpis admina/relayera bez
roli operatora nie spełnia kontroli.

## Co się upraszcza względem wcześniejszego wariantu

- Brak konfliktu operatorów w kontrakcie (nie ma zapisanego approval do nadpisania).
  Backend trzyma jedno aktywne approval na polecenie: ten sam operator jest
  idempotentny, drugi operator przy ważnym approval dostaje `409`.
- Brak trwałego „odwołania approval”: approval wygasa swoim deadline, a odebranie
  roli operatorowi blokuje `completeStage` natychmiast, bo rola jest sprawdzana
  w chwili wykonania. Ponowne nadanie roli **nie ożywia** wygasłego approval.
- Brak indexowania approval i reorg approval; approval nie jest stanem łańcucha.
- Odmowa operatora to brak podpisu: kandydat czeka do wygaśnięcia i przechodzi w
  FAILED. Formalna decyzja „odrzuć” nie istnieje w MVP.

## Czas i awarie

- Deadline stacji ma obejmować pinowanie, inspekcję i inclusion `completeStage`;
  kontrakt ogranicza go przez `maxDeadlineWindow` (DEC-11, robocze 3600 s).
  `approvalDeadline` domyślnie jak deadline stacji.
- Po wygaśnięciu przed send: FAILED; nowy raport według
  [reguły zastępowania](corrections.md). Nie wydłużaj terminu w bazie.
- Po send: najpierw uzgodnij receipt; czas nie dowodzi, że tx wygasła przed wykonaniem.
- Odmowa portfela, zła sieć i brak sesji są jawne; UI nie pokazuje sukcesu.
- Unieważnienie certyfikatu to `revokeStation` on-chain: kontrakt odrzuca kolejne
  `completeStage` tej stacji niezależnie od backendu.

## Minimalna macierz testów

| Przypadek | Oczekiwany wynik |
|---|---|
| Podpis stacji + approval niezależnego operatora, ten sam hash | jeden StageCompleted z `qaOperator` |
| Brak approval / brak podpisu stacji | revert, brak przejścia |
| Ten sam adres jako stacja i operator | revert |
| Approval dla innego batchId, stageId lub reportHash | revert |
| Approval po `approvalDeadline` | revert |
| qaPassed=false mimo approval | tylko `StageRejected`, bez przejścia |
| Operator bez QA_INSPECTOR_ROLE albo z odebraną rolą | revert |
| Stacja odwołana lub wygasła przed wykonaniem | revert |
| Podmieniony pomiar po podpisie | odrzucenie hash/podpisu w backendzie i kontrakcie |
| Ponowne `POST approval` tego samego operatora | idempotentne, jedno zapisane approval |
| Approval od operatora, gdy zapisano już inne ważne | konflikt `409` (pierwszy ważny wygrywa) lub idempotencja przy tym samym adresie |

Osoba 1 egzekwuje reguły w Solidity, osoba 2 prowadzi kandydatów i `qa_approvals`,
osoba 3 prezentuje dwa kroki i podpis, osoba 4 testuje brakujące dowody
i wyścigi.
