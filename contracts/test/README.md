# Testy kontraktu

**Język:** Solidity (`*.t.sol`), runner Forge, `forge-std/Test`.

Zacznij od `SupplyChain.t.sol`. Setup wdraża nowy kontrakt, tworzy testowe
adresy i jawnie nadaje role. Użyj `vm.prank`, `vm.expectRevert`, `vm.expectEmit`,
`vm.warp` i `vm.sign`; klucze są wyłącznie lokalnymi danymi testu.

Testuj: poprawny etap, zła kolejność, nieznana partia/stacja, brak roli relayera,
QA fail, brak ręcznego zatwierdzenia, wygasły podpis, replay, inny chainId/adres
kontraktu i każdą zmianę podpisanego pola. Po rewercie stan i nonce są niezmienione.
Porównuj argumenty eventów i stan, nie tylko brak revertu.

Dla kontroli przetestuj oba niezależne klucze: stacja i operator zmiany.
Sam podpis stacji, samo approval operatora, approval dla innego hash
oraz ten sam adres po obu stronach nie mogą zamknąć kontroli.
Po odebraniu roli operatorowi stare approval nie może dać przejścia.

Dodaj fuzz dla nonce, identyfikatorów i kolejności; invariant: bez autoryzowanego
poprawnego podpisu nie można przesunąć produkcji. Wspólny wektor z
[fixtures](../../shared/fixtures/README.md) musi przechodzić także tutaj.
Po inicjalizacji: `forge test`, a dla pliku `forge test --match-path test/SupplyChain.t.sol`.
