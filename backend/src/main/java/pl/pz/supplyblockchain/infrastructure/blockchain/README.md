# Blockchain i indexer

**Język:** Java 21; Web3j obsługuje RPC, kodowanie wywołań i podpis transakcji.

Plan: `Web3jRelayerChainGateway`, `Web3jEip712Verifier`, `RelayerTransactionSender`,
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
(`eth_call` na `latest`, ścisłe dekodowanie ABI). `StationCertificateConfig` tworzy porty
`StationCertificateVerifier` i `StationRegistry` na podstawie `supplychain.chain` oraz
`supplychain.station.trusted-issuers`; `SubmitStageService` wywołuje weryfikator dla każdego
nowego zgłoszenia i odrzuca błędy rejestru jako niedostępność zależności, nie pozytywny wynik.
Zamknięcie kontroli wymaga też `StageApproval` operatora (z adresem innym niż stacja);
podpis relayera nie jest drugim dowodem QA.
EIP-712 jest zaimplementowany ręcznie (jedna stała struktura: Keccak + odzyskanie adresu)
i musi przejść wspólny wektor Python/Java/TS/Solidity.

Relayer: jeden writer na konto/sieć, nonce konta relayera, własne podpisywanie, własny
dostawca opłat EIP-1559 z konfigurowalnym minimalnym tipem. Zapisz podpisaną transakcję
przed wysłaniem; po timeoucie wyślij ponownie identyczne bajty i uzgodnij hash.
Bez fee-bump/replacement w MVP; zablokowana transakcja: procedura ręczna.

Lokalny `ChainEventIndexer` działa po włączeniu `supplychain.indexer.enabled` i odmawia
pracy na chainId innym niż 31337. `deployment-block` bierze się z `deploymentBlock`
wdrożenia; lokalny runner przekazuje go jako `CHAIN_DEPLOYMENT_BLOCK`.
Indexer skanuje `latest` (wliczony blok to jedno wymagane potwierdzenie), zakresami
do 10 000 bloków i wyłącznie logi `BatchCreated`, `StageCompleted`, `StageRejected`
z skonfigurowanego adresu kontraktu. Kolejność to
`(blockNumber, transactionIndex, logIndex)`.

V7 przechowuje surowe logi, projekcje batch/stage, checkpoint i ostatnie 64 kotwice.
Zapis zakresu jest atomowy. Reorg wycofuje niekanoniczne eventy, odbudowuje projekcje
i oznacza odłączone, wcześniej potwierdzone polecenia jako `FAILED/CHAIN_REORG`;
indexer potwierdza polecenie tylko po dopasowaniu danych eventu do próby
transakcyjnej i polecenia. Worker nie nadaje `CONFIRMED` z receiptu.
Świeżość projekcji wygasa po 10 sekundach bez poprawnego cyklu indeksowania.
Lokalny runner ustawia `CHAIN_INDEXER_ENABLED=true` i przekazuje
`CHAIN_DEPLOYMENT_BLOCK` z `shared/deployments/local.json`. Przy ręcznym starcie
backendu ustaw te zmienne oraz `CHAIN_RPC_URL` i `CONTRACT_ADDRESS`.

Testy dekodera i odczytu są w testach jednostkowych; `ChainEventStoreIT` wymaga
`TEST_DB_URL` i sprawdza atomowość/deduplikację/rollback na PostgreSQL.
Testy na Anvil powinny dodatkowo obejmować `evm_snapshot`/`evm_revert`.
