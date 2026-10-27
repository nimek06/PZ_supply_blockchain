# Testy aplikacji Java

**Język:** Java 21; JUnit, MockMvc, Testcontainers, stuby HTTP/RPC.

Plan: `SubmitStageUseCaseTest`, `BatchControllerTest`, `SignatureVectorTest`,
`RelayerWorkerIT`, `PersistenceIT`, `ChainEventIndexerIT`.
Gdy rośnie liczba testów, odtwórz podpakiety api/application/domain/infrastructure.

Sprawdź schemat i statusy, idempotencję, poprawny i zmieniony podpis,
restart między krokami, konkurencję nonce relayera, revert i reorg (okno rescanu, Anvil `evm_snapshot`/`evm_revert`).
Test domeny nie wymaga Spring; test integracji DB korzysta z PostgreSQL.
Test podpisu odczytuje wspólne fixture'y (ręczny EIP-712, wspólny wektor), nie inną ręcznie utworzoną strukturę.

Testy muszą jawnie wskazywać profil/testową sieć i chronić przed połączeniem
z prawdziwą bazą/RPC. `@SpringBootTest` nie jest domyślnym wyborem dla każdej klasy.
Po inicjalizacji: `./mvnw verify`; sprawdź, czy runner rzeczywiście odkrył IT.

Wymagania PDF dodają przypadki certyfikatu stacji (wystawca, adres, ważność,
odwołanie, zgodność z rejestrem on-chain) i niezależności podpisów kontroli. Test API może sprawdzać
WAITING_FOR_QA i `outcome=REJECTED` (`202`), ale egzekucję dwóch kluczy potwierdź również integracją
z rzeczywistym kontraktem na Anvil.
