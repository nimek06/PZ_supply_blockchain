# Blockchain i indexer

**Język:** Java 21; Web3j.

Plan: `Web3jChainGateway`, `Eip712SignatureVerifier`, `RelayerTransactionSender`,
`ChainEventIndexer`. Wrapper kontraktu generuj z ABI w buildzie do
`target/generated-sources/`, w osobnym pakiecie; nie edytuj go ręcznie.

Podpis stacji: kanoniczny hash -> digest EIP-712 -> recover -> rola i zgodność
pól. Nie myl go z podpisem transakcji wykonanym kluczem relayera.
Weryfikacja certyfikatu off-chain musi wskazać ten sam adres.
Zamknięcie kontroli wymaga też approval operatora zmiany dla tego samego hash,
z adresem innym niż signer stacji. Podpis relayera nie jest drugim dowodem QA.
Ręcznie utworzony hash EIP-712 musi przejść wspólny wektor Solidity/Python.

Relayer: jeden writer na konto/sieć, nonce transakcyjny, zapis danych/hash
przed send, sprawdzenie receipt i revertu. Timeout nie dowodzi niewysłania.
Obsłuż replacement tej samej operacji z tym samym nonce i opłatami EIP-1559.

Indexer czyta logi zakresami od deployment block, zapisuje checkpoint.
Deduplikacja: chainId, contract, txHash, logIndex; do reorg przechowuj również
blockHash i pozycję bloku. Uwzględnij potwierdzenia i odtworzenie po reorg.
Testy: stub RPC dla błędów i Anvil dla rzeczywistych podpisów/events.
