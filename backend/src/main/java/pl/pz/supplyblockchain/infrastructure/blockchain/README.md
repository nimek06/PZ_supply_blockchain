# Blockchain i indexer

**Język:** Java 21; Web3j tylko do kodowania wywołań i podpisu transakcji.

Plan: `Web3jChainGateway`, `Eip712SignatureVerifier`, `RelayerTransactionSender`,
`ChainEventIndexer` (indexer: osoba 1). Kodowanie wywołań z ABI (opcjonalnie wygenerowany
wrapper w `target/generated-sources/`, bez ręcznej edycji); nie używaj `.send()`
z wrappera ani `DefaultGasProvider`.

Podpis stacji (`StageCompletion`, bez nonce): kanoniczny hash -> digest EIP-712 -> recover ->
zgodność z rejestrem stacji i polami. Nie myl go z podpisem transakcji relayera.
Certyfikat JSON (weryfikator w Javie: osoba 1) musi wskazać ten sam adres co rejestr.
`Eip712StationCertificateVerifier` (T010) liczy keccak256(JCS(payload)), digest EIP-712
`StationCertificate(bytes32 payloadHash)` w domenie `SupplyChainStationRegistry`/`1` z chainId
i verifyingContract z certyfikatu, odzyskuje wystawcę, sprawdza allowlistę i wdrożenie
(`StationCertificatePolicy`), okno ważności oraz `getStation` przez `Web3jStationRegistry`
(`eth_call` na `latest`, ścisłe dekodowanie ABI). Beany i allowlista w konfiguracji powstają
przy wpięciu w odbiór raportu.
Zamknięcie kontroli wymaga też `StageApproval` operatora (z adresem innym niż stacja);
podpis relayera nie jest drugim dowodem QA.
EIP-712 jest zaimplementowany ręcznie (jedna stała struktura: Keccak + odzyskanie adresu)
i musi przejść wspólny wektor Python/Java/TS/Solidity.

Relayer: jeden writer na konto/sieć, nonce konta relayera, własne podpisywanie, własny
dostawca opłat EIP-1559 z konfigurowalnym minimalnym tipem. Zapisz podpisaną transakcję
przed wysłaniem; po timeoucie wyślij ponownie identyczne bajty i uzgodnij hash.
Bez fee-bump/replacement w MVP; zablokowana transakcja: procedura ręczna.

Indexer czyta logi oknami od deployment block i zapisuje checkpoint z numerem i hashem bloku.
Okno ma maksymalnie 10 000 bloków, bo taki jest limit publicznego RPC Amoy.
Obserwację logu identyfikują chainId, contract, blockHash i logIndex. Po reorg ta sama tx może
trafić do innego bloku, z innym blockHash i logIndex. Dlatego txHash służy tylko do łączenia logu
z poleceniem.
Bez pełnej odbudowy po reorg: głębokość potwierdzeń + okno ponownego skanu
ostatnich N bloków z porównaniem block hash. Zdarzenia: `StageCompleted`, `StageRejected`,
`BatchCreated`, `StationRegistered`, `StationRevoked`.
Testy: stub RPC dla błędów i Anvil (reorg: `evm_snapshot`/`evm_revert`).
Pomiary Amoy, scenariusze reorgu i projekt cyklu: `docs/weekly_plans/week2/adam-indexer-notes.md`.
