# Blockchain i indexer

**Język:** Java 21; Web3j tylko do kodowania wywołań i podpisu transakcji.

Plan: `Web3jChainGateway`, `Eip712SignatureVerifier`, `RelayerTransactionSender`,
`ChainEventIndexer` (indexer: osoba 1). Kodowanie wywołań z ABI (opcjonalnie wygenerowany
wrapper w `target/generated-sources/`, bez ręcznej edycji); nie używaj `.send()`
z wrappera ani `DefaultGasProvider`.

Podpis stacji (`StageCompletion`, bez nonce): kanoniczny hash -> digest EIP-712 -> recover ->
zgodność z rejestrem stacji i polami. Nie myl go z podpisem transakcji relayera.
Certyfikat JSON (weryfikator w Javie: osoba 1) musi wskazać ten sam adres co rejestr.
Zamknięcie kontroli wymaga też `StageApproval` operatora (z adresem innym niż stacja);
podpis relayera nie jest drugim dowodem QA.
EIP-712 jest zaimplementowany ręcznie (jedna stała struktura: Keccak + odzyskanie adresu)
i musi przejść wspólny wektor Python/Java/TS/Solidity.

Relayer: jeden writer na konto/sieć, nonce konta relayera, własne podpisywanie, własny
dostawca opłat EIP-1559 z konfigurowalnym minimalnym tipem. Zapisz podpisaną transakcję
przed wysłaniem; po timeoucie wyślij ponownie identyczne bajty i uzgodnij hash.
Bez fee-bump/replacement w MVP; zablokowana transakcja: procedura ręczna.

Indexer czyta logi zakresami od deployment block, zapisuje checkpoint.
Deduplikacja: chainId, contract, txHash, logIndex; przechowuj blockHash.
Bez pełnej odbudowy po reorg: głębokość potwierdzeń + okno ponownego skanu
ostatnich N bloków z porównaniem block hash. Zdarzenia: `StageCompleted`, `StageRejected`,
`BatchCreated`, `StationRegistered`, `StationRevoked`.
Testy: stub RPC dla błędów i Anvil (reorg: `evm_snapshot`/`evm_revert`).
