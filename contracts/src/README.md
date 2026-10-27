# Kod kontraktu

**Język:** Solidity (`.sol`).

Zacznij od `SupplyChain.sol`: role, stan partii, oczekiwany etap, nonce stacji,
zatwierdzenia QA, EIP-712 i zdarzenia. Używaj dziedziczenia z OpenZeppelin
`AccessControl` i `EIP712`. Rozdziel pliki dopiero gdy istnieje osobny,
testowalny zakres; nie twórz pustych interfejsów/libraries na zapas.

Implementuj według [protokołu](../../docs/protocol.md):
1. Model stanu i tworzenie partii z autoryzacją.
2. Odczyty stanu i nonce bez nieograniczonych pętli.
3. `completeStage`: rola relayera, recover stacji, rola stacji, deadline,
   nonce, kolejność i QA; aktualizacja stanu; event.
4. Jawne zatwierdzenie konkretnego reportHash przez operatora zmiany z rolą QA.
   Zapisz adres zatwierdzającego, powiązany z partią/etapem/hash.
   Zamknięcie kontroli wymaga też EIP-712 stacji i różnych adresów obu signerów.

Custom errors powinny odróżniać brak roli, nieznaną partię, zły etap, podpis,
replay, wygaśnięcie i QA. Eventy mają pozwalać odtworzyć historię.
Nie wykonuj wywołań zewnętrznych bez potrzeby. Najpierw zaprojektuj
[testy](../test/README.md), potem implementuj przejście.
Nie kopiuj do storage JSON, certyfikatów ani surowych podpisów.
Kontrakt sprawdza aktualne role; certyfikat stacji jest sprawdzany off-chain
i nie zastępuje RBAC.
