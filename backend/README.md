# Backend - osoba 2

**Języki:** Java 21, SQL, YAML/XML konfiguracji.
**Narzędzia:** aktualna wspierana wersja Spring Boot (wybierz przy inicjalizacji;
sprawdź zgodność z Web3j i Jackson), Maven Wrapper, Web3j (tylko kodowanie
wywołań i podpis), Spring Data JPA, PostgreSQL, Flyway, Spring RestClient,
Kubo (lokalnie/CI) i Pinata (demo) przez port `ReportStorage`.
Zakres: API, persystencja, relayer, IPFS, auth. [Indexer](src/main/java/pl/pz/supplyblockchain/infrastructure/blockchain/README.md),
weryfikator certyfikatu w Javie i CI należą do osoby 1 (docelowy podział: `docs/team-split.md`).
Zmiany projektowe: [changes-v2](../docs/changes-v2.md) - wiążące.
Według [PDF](../docs/source-alignment.md) backend jest integratorem
programowego prototypu, nie nowej fizycznej linii.

## Co utworzyć

W korzeniu: `pom.xml`, `mvnw`, `mvnw.cmd`, pliki `.mvn/wrapper/`.
Klasa startowa: `src/main/java/pl/pz/supplyblockchain/SupplyBlockchainApplication.java`.
Nie ma jeszcze aplikacji ani manifestu; start opisuje
[development](../docs/development.md).

Podział pakietów i resources opisuje [src](src/README.md).
API nie powinno bezpośrednio korzystać z JPA, Pinaty ani Web3j.
Konfiguracja Spring łączy porty z adapterami.

## Kolejność implementacji

Przed DTO i migracjami przeczytaj [reguły etapów](../docs/production-rules.md),
[poprawki i retry](../docs/corrections.md), [politykę approval](../docs/quality-approval.md)
oraz [macierz ról i auth](../docs/access-control.md).
Auth: SIWE (EIP-4361) tylko do wydania tokenu, dalej `Authorization: Bearer`
(token losowy, w bazie skrót SHA-256); bez cookie i CSRF.

1. Uruchom pustą aplikację i test startu z profilem testowym.
2. Zdefiniuj records DTO i walidację zgodną ze wspólnym schematem.
3. Zaimplementuj kanonizację (JCS), hash, ręczną weryfikację EIP-712
   (`StageCompletion` bez nonce, `StageApproval`; wspólny wektor) oraz zgodność
   stacji z certyfikatem JSON i rejestrem on-chain (`getStation`).
4. Dodaj migracje partii/projekcji, poleceń, idempotencji, `qa_approvals`,
   podpisanych transakcji i checkpointów; numer partii `B-RRRR-NNNN`
   (`batchId = keccak256(utf8(batchNumber))`).
5. API przyjmuje polecenie dopiero po trwałym zapisie i zwraca `202`.
6. Dodaj port `ReportStorage` z adapterami Kubo i Pinata; zapisuj CID przed kolejnym krokiem.
7. Zakoduj wywołania kontraktu przez Web3j (z ABI), dodaj pojedynczy writer relayera
   z własnym podpisem i dostawcą opłat EIP-1559.
8. Indexer logów (osoba 1): głębokość potwierdzeń, okno ponownego skanu, deduplikacja.
9. Udostępnij projekcję, statusy i historię; podłącz symulator i frontend.

Etap kontroli po pinowaniu przechodzi w WAITING_FOR_QA. Chroniona lista
kandydatów umożliwia operatorowi zmiany obejrzenie raportu i podpisanie
`StageApproval` (EIP-712) portfelem poza łańcuchem; backend zapisuje podpis
w `qa_approvals`. Dopiero wtedy relayer wysyła `completeStage` z dwoma podpisami.
`qaPassed=false` kończy się `StageRejected` (`202`, `outcome=REJECTED`), nie `422`.
Szczegóły: [flows](../docs/flows.md) i [certyfikaty](../docs/station-identity.md).
W PostgreSQL przechowuj lokalny rejestr certyfikatów/odwołań (audyt, zgodny
z rejestrem on-chain); nie publikuj go bez potrzeby w publicznym paszporcie.

## Wzorce pracy

Używaj constructor injection i `@ConfigurationProperties` z walidacją.
DTO nie są encjami JPA. Transakcje bazy są krótkie: nie trzymaj otwartego
`@Transactional` podczas HTTP lub oczekiwania na receipt.
Mapuj wyjątki na Problem Details przez wspólny `@RestControllerAdvice`.
Nie łap `Exception` tylko po to, żeby zwrócić `null` lub pustą listę.

Trwała kolejka to PostgreSQL; Redis/RabbitMQ, `@Async` i `BlockingQueue` nie są
kolejką. Wyłącz równoległe przydzielanie nonce konta relayera (stacje nonce nie mają).
Reguła zastępowania raportu: [changes-v2](../docs/changes-v2.md).

## Konfiguracja i dostęp

Sekrety: klucz relayera, JWT Pinaty (demo), hasło bazy - zmienne środowiskowe lub
magazyn sekretów. Konfiguracja publiczna: RPC URL, chainId, adres kontraktu,
gateway IPFS, dozwolone originy CORS, minimalny tip EIP-1559, głębokość potwierdzeń, N bloków okna rescanu. RPC URL również może zawierać sekretny token.
Nie loguj go w całości. Profile `local` i `test` muszą być jawne.
Nie używaj `ddl-auto=update`; schemat bazy zmieniają migracje.

Publiczny odczyt paszportu nie oznacza publicznego endpointu statusu poleceń,
tworzenia partii ani administracji. Wprowadź i testuj docelową autoryzację.

## Sprawdzenie

Po inicjalizacji: `./mvnw verify`. Testy JUnit, MockMvc, PostgreSQL przez
Testcontainers (w tym Kubo), stub Pinaty/RPC i Anvil (test reorg: `evm_snapshot`/`evm_revert`) do integracji kontraktu.
Kryterium: poprawny podpis, trwałe przyjęcie, bezpieczny restart, jeden event
dla ponowionego żądania i stan `CONFIRMED` dopiero po potwierdzeniu.
