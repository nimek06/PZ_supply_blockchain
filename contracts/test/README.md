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
