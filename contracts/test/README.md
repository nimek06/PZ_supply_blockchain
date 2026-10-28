# Testy kontraktu

**Język:** Solidity (`*.t.sol`), runner Forge, `forge-std/Test`.

Zacznij od `SupplyChain.t.sol`. Setup wdraża nowy kontrakt, tworzy testowe
adresy i jawnie nadaje role. Użyj `vm.prank`, `vm.expectRevert`, `vm.expectEmit`,
`vm.warp` i `vm.sign`; klucze są wyłącznie lokalnymi danymi testu.

Testuj: poprawny etap, zła kolejność, nieznana partia/stacja, brak roli relayera,
stacja wyrejestrowana/wygasła/poza `stageMask`, `qaPassed=false` (event `StageRejected`,
stan partii bez zmian), brak podpisu approval, wygasły `deadline`/`approvalDeadline`,
deadline powyżej okna maksymalnego, ponowne ukończenie tego samego `(batchId, stageId)`,
inny chainId/adres kontraktu i każdą zmianę podpisanego pola.
Po rewercie stan jest niezmieniony.
Porównuj argumenty eventów i stan, nie tylko brak revertu.

Dla kontroli przetestuj oba niezależne klucze: stacja i operator zmiany.
Sam podpis stacji, sam podpis approval operatora, approval dla innego hash
oraz ten sam adres po obu stronach nie mogą zamknąć kontroli.
Po odebraniu roli operatorowi stary podpis approval nie może dać przejścia.
Testuj też `createBatch(string)` (`batchId` = keccak256 numeru, duplikat numeru).

Dodaj fuzz dla identyfikatorów, deadline i kolejności; invariant: bez autoryzowanego
poprawnego podpisu nie można przesunąć produkcji. Wspólny wektor z
[fixtures](../../shared/fixtures/README.md) musi przechodzić także tutaj.
Po inicjalizacji: `forge test`, a dla pliku `forge test --match-path test/SupplyChain.t.sol`.

## Wspólny wektor podpisu (`SignatureVector.t.sol`)

`test_SharedVector` czyta `shared/fixtures/signature-vector.json` i jest pomijany (SKIP),
dopóki plik nie istnieje. Domena: `SupplyChain`/`1`, chainId `31337`, kontrakt pod stałym
adresem `0x5FbDB2315678afecb367f032d93F642f64180aa3` (test wdraża go tam przez `deployCodeTo`).
Oczekiwane ścieżki JSON (pozostałe pola pliku są dowolne):

```text
.domain.chainId, .domain.verifyingContract
.stageCompletion.message.{batchId, stageId, reportHash, qaPassed, deadline}
.stageCompletion.{digest, signature, signer}
.stageApproval.message.{batchId, stageId, reportHash, deadline}
.stageApproval.{digest, signature, signer}
```

Do czasu publikacji wektora działa wektor tymczasowy (`test_ProvisionalVector_*`): klucze
Anvil #1 (stacja) i #2 (operator), digesty i podpisy wygenerowane niezależnie przez `cast`.
