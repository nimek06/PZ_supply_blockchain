# Kod kontraktu

**Język:** Solidity (`.sol`).

Zacznij od `SupplyChain.sol`: role, rejestr stacji, stan partii, oczekiwany etap,
EIP-712 (`StageCompletion` bez nonce, `StageApproval`) i zdarzenia. Używaj dziedziczenia z OpenZeppelin
`AccessControl` i `EIP712`. Rozdziel pliki dopiero gdy istnieje osobny,
testowalny zakres; nie twórz pustych interfejsów/libraries na zapas.

Implementuj według [protokołu](../../docs/protocol.md):
1. Model stanu i `createBatch(string batchNumber)` z autoryzacją (`batchId = keccak256(utf8(batchNumber))`).
2. Rejestr stacji: `registerStation`/`revokeStation`/`getStation`, `setMaxDeadlineWindow`.
3. Odczyty stanu (`getBatch`) bez nieograniczonych pętli.
4. `completeStage`: rola relayera, kolejność, deadline (z oknem maksymalnym),
   recover i rejestr stacji (aktywna, ważność, `stageMask`); `qaPassed=false` ->
   `StageRejected` bez zmiany stanu; aktualizacja stanu; event.
5. Kontrola (`stageId == 2`): z `approvalSignature` odzyskaj operatora (`StageApproval`),
   sprawdź `approvalDeadline`, aktualną `QA_INSPECTOR_ROLE` i adres różny od stacji.
   Brak `approveStage` i storage approval.

Custom errors powinny odróżniać brak roli, nieznaną partię, zły etap, podpis,
replay, wygaśnięcie, stację poza rejestrem/zakresem i QA. Eventy mają pozwalać odtworzyć historię.
Nie wykonuj wywołań zewnętrznych bez potrzeby. Najpierw zaprojektuj
[testy](../test/README.md), potem implementuj przejście.
Nie kopiuj do storage JSON, certyfikatów ani surowych podpisów.
Kontrakt sprawdza aktualne role i rejestr stacji; JSON certyfikatu stacji
jest tylko poświadczeniem audytowym i musi zgadzać się z rejestrem.
