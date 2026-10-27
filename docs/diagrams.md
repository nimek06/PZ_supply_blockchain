# Mapa systemu i zależności

To **projekt docelowy**, nie diagram istniejącego kodu. Czytaj razem z
[architekturą](architecture.md), [decyzjami](decisions.md) i
[przepływami](flows.md). Mermaid renderuje się w podglądzie Markdown
obsługującym Mermaid oraz na GitHubie.
Nadrzędny zakres opisuje [mapa PDF](source-alignment.md). QA oznacza operatora
zmiany; jego niezależny klucz współautoryzuje zamknięcie kontroli.
Certyfikaty stacji są off-chain, a nie nową fizyczną infrastrukturą.

## 1. Architektura aplikacji

Strzałka oznacza inicjatora wywołania, a nie kierunek importu.
Odpowiedź wraca tym samym połączeniem. Indexer odpytuje RPC; kontrakt
nie wywołuje HTTP do backendu.

<!-- mermaid-checked: no \n, no em-dash/en-dash, no {} in labels, subgraphs are id["label"], arrows are -->|"label"|, all subgraphs closed by end, ids unique -->
```mermaid
flowchart TD
    subgraph ActorsA["Uzytkownicy i stacje"]
        VisitorA["Publiczny odbiorca"]
        OperatorA["Operator i QA"]
        SimulatorA["Symulator Python"]
    end
    subgraph ClientA["Przegladarka"]
        FrontendA["React i TypeScript"]
        WalletA["Portfel operatora zmiany"]
    end
    subgraph BackendA["Jeden proces Spring Boot"]
        ApiA["REST API i autoryzacja"]
        WorkerA["Worker i signer relayera"]
        IndexerA["Indexer zdarzen"]
    end
    DatabaseA[("PostgreSQL")]
    PinataA["Pinata API"]
    GatewayA["IPFS Gateway"]
    RpcA["EVM RPC - Anvil albo Amoy"]
    ContractA["Kontrakt SupplyChain"]
    VisitorA -->|"Odczyt bez portfela"| FrontendA
    OperatorA -->|"Panel i decyzje QA"| FrontendA
    SimulatorA -->|"HTTPS - raport podpisany i ID certyfikatu"| ApiA
    SimulatorA -->|"Odczyt nonce stacji"| RpcA
    FrontendA -->|"HTTPS - dane i polecenia"| ApiA
    FrontendA -->|"Odczyt raportu CID"| GatewayA
    FrontendA -->|"Weryfikacja on-chain"| RpcA
    FrontendA -->|"Prosba o transakcje QA"| WalletA
    WalletA -->|"Podpisana transakcja"| RpcA
    ApiA -->|"Zapis polecen i odczyt projekcji"| DatabaseA
    ApiA -->|"Weryfikacja rol i stanu"| RpcA
    WorkerA -->|"Zadania i wyniki prob"| DatabaseA
    WorkerA -->|"Pinowanie raportu"| PinataA
    WorkerA -->|"Wyslanie i uzgodnienie transakcji"| RpcA
    IndexerA -->|"Odczyt blokow i logow"| RpcA
    IndexerA -->|"Projekcja i checkpoint"| DatabaseA
    RpcA -->|"Wywolanie EVM"| ContractA
```

### Technologie i procesy

| Warstwa | Technologia / wersja bazowa | Rola |
|---|---|---|
| Kontrakt | Solidity 0.8.24+, OpenZeppelin, Foundry | reguły i ostateczna autoryzacja etapów |
| Backend | Java 21, Spring Boot 3.3+, Maven, Web3j | API, trwałe polecenia, relayer i indexer |
| Baza | PostgreSQL + Flyway + JPA | zadania, próby, projekcje i checkpoint |
| Symulator | Python 3.11+, Pydantic, eth-account, httpx | podpisane dane i scenariusze |
| Frontend | React 18/19, TypeScript, Vite, Wagmi v2/Viem | UI, portfel QA i paszport |
| Raporty | Pinata i publiczny gateway IPFS | zapis i odczyt JSON |
| Sieć | Anvil 31337 / Polygon Amoy 80002 | lokalna i testowa egzekucja EVM |

Dokładne wersje są do przypięcia podczas inicjalizacji, nie wynikają ze znaku `+`.
Nie ma osobnego serwera kolejek i osobnego mikroserwisu indexera.
Pinata API pinuje dane, gateway je odczytuje; brak strzałki między nimi oznacza,
że replikacji IPFS nie implementujemy we własnym kodzie.

## 2. Zależności kodu backendu

Tutaj strzałka oznacza zależność/import albo implementację wskazanego interfejsu,
**nie** fizyczne wywołanie HTTP. W runtime aplikacja wywoła port, a Spring
podstawi adapter. Pozwala to testować przypadki użycia bez RPC i Pinaty.

<!-- mermaid-checked: no \n, no em-dash/en-dash, no {} in labels, subgraphs are id["label"], arrows are -->|"label"|, all subgraphs closed by end, ids unique -->
```mermaid
flowchart LR
    subgraph PresentationB["Transport"]
        ControllerB["api - kontrolery i DTO"]
    end
    subgraph ApplicationB["Przypadki uzycia"]
        UseCasesB["application"]
        PortsB["application ports"]
    end
    DomainB["domain - czysta Java"]
    subgraph InfrastructureB["Adaptery"]
        ChainB["blockchain"]
        StorageB["ipfs"]
        PersistenceB["persistence"]
        QueueB["queue - scheduler"]
    end
    ConfigB["config - kompozycja Spring"]
    ControllerB -->|"Wywoluje"| UseCasesB
    UseCasesB -->|"Uzywa"| PortsB
    UseCasesB -->|"Reguly i typy"| DomainB
    PortsB -->|"Typy"| DomainB
    ChainB -->|"Implementuje"| PortsB
    StorageB -->|"Implementuje"| PortsB
    PersistenceB -->|"Implementuje"| PortsB
    QueueB -->|"Uruchamia przypadek uzycia"| UseCasesB
    ConfigB -->|"Konfiguruje"| InfrastructureB
    ConfigB -->|"Konfiguruje"| PresentationB
```

### Inwentarz komponentów

| Komponent | Odpowiedzialność | Zabroniony skrót |
|---|---|---|
| BatchController / CommandController | walidacja HTTP, kontekst tożsamości, wywołanie use case | Web3j lub JPA bezpośrednio w kontrolerze |
| SubmitStageUseCase | spójność raportu, podpis, idempotencja, trwałe przyjęcie | HTTP 202 przed zatwierdzeniem zapisu DB |
| ProcessStageCommand | kroki pinowania, QA i wysłania | jedna długa transakcja SQL obejmująca RPC |
| ChainGateway / ReportStorage | interfejsy aplikacji | Web3j Response w modelu domenowym |
| Web3jChainGateway | ABI, role, nonce, send/receipt/logs | uznanie timeout za dowód niewysłania |
| PinataReportStorage | pinowanie i walidacja CID | sukces z pustym CID |
| CommandRepository | trwałość i constraints | deduplikacja wyłącznie w pamięci |
| RelayerWorker | lease, wznawianie i pojedynczy writer | wielowątkowe przydzielanie nonce konta |
| ChainEventIndexer | kanoniczna historia i checkpoint | brak cofania projekcji po reorg |
| domain | typy i invariants | zależność od Spring/JPA/Web3j |

## 3. Zależności buildów i formatów

Strzałki oznaczają **co powstaje z czego**. To nie ruch sieciowy.
Shared nie jest piątym serwisem i nie uruchamia procesu.

<!-- mermaid-checked: no \n, no em-dash/en-dash, no {} in labels, subgraphs are id["label"], arrows are -->|"label"|, all subgraphs closed by end, ids unique -->
```mermaid
flowchart LR
    ProtocolC["docs protocol i zatwierdzone decyzje"]
    SchemasC["shared schemas"]
    FixturesC["shared fixtures"]
    SolidityC["contracts src"]
    CompilerC["Forge i solc"]
    AbiC["shared abi"]
    DeploymentC["shared deployments"]
    JavaC["Java DTO i wrapper Web3j"]
    PythonC["Python models i signing"]
    TypescriptC["TypeScript API i Viem"]
    ProtocolC -->|"Definiuje format"| SchemasC
    SchemasC -->|"Dane zgodne ze schematem"| FixturesC
    SchemasC -->|"Typy podpisu i reguly"| SolidityC
    SolidityC -->|"Kompilacja"| CompilerC
    CompilerC -->|"Eksport ABI"| AbiC
    CompilerC -->|"Wdrozenie i weryfikacja"| DeploymentC
    AbiC -->|"Generowanie wrappera"| JavaC
    AbiC -->|"Typowane wywolania"| TypescriptC
    SchemasC -->|"DTO i walidacja"| JavaC
    SchemasC -->|"Modele i walidacja"| PythonC
    SchemasC -->|"Typy i walidacja"| TypescriptC
    FixturesC -->|"Wektory zgodnosci"| JavaC
    FixturesC -->|"Wektory zgodnosci"| PythonC
    FixturesC -->|"Wektory zgodnosci"| TypescriptC
    FixturesC -->|"Weryfikacja podpisu"| SolidityC
    DeploymentC -->|"Siec i adres"| JavaC
    DeploymentC -->|"Domena i nonce"| PythonC
    DeploymentC -->|"Siec i adres"| TypescriptC
```

Generowane klasy Java trafiają do `backend/target/generated-sources/`,
nie do ręcznie utrzymywanego modelu. Po zmianie podpisywanego pola trzeba
zaktualizować wszystkie cztery moduły, nie tylko schema JSON.
Zmiana adresu wdrożenia wymaga nowej domeny; stary podpis nie przejdzie.

## 4. Katalog połączeń runtime

Porty to lokalna propozycja, nie sztywne adresy w kodzie. Na zewnątrz HTTPS.
API wewnątrz jednego procesu nie potrzebuje brokera ani połączenia HTTP.

| Inicjator -> odbiorca | Protokół / lokalny port | Dane | Uprawnienie i błąd |
|---|---|---|---|
| przeglądarka -> frontend | HTTP 5173 | pliki SPA | publiczne; błąd ładowania |
| frontend -> backend | REST JSON 8080 | odczyty, tworzenie partii, prywatne statusy | sesja dla zapisu/statusu; Problem Details |
| symulator -> backend | REST JSON 8080 | raport + EIP-712, login/status | podpis stacji; sesja dla prywatnego GET |
| symulator -> RPC | JSON-RPC 8545 | odczyt nonce i domeny | brak klucza relayera; jawny błąd RPC |
| backend -> PostgreSQL | PostgreSQL 5432 | zadania, próby, projekcja | osobne konto bazy, brak publicznego portu w deploymencie |
| backend -> Pinata | HTTPS 443 | raport JSON | JWT tylko na serwerze; timeout/401/429/5xx |
| backend -> RPC | JSON-RPC 8545 / HTTPS 443 | role, stan, tx, receipt, logi | klucz relayera tylko do podpisu; RPC nigdy nie dostaje go jako JSON |
| portfel operatora zmiany -> RPC | JSON-RPC | podpisana transakcja zatwierdzenia tego samego raportu | QA_INSPECTOR_ROLE, inny adres niż stacja, gaz z konta operatora |
| frontend -> gateway | HTTP lokalnie / HTTPS 443 | raport z CID | publiczne; limit rozmiaru, hash i schema |
| frontend -> RPC | JSON-RPC | niezależny odczyt stanu/logu | publiczny endpoint bez prywatnego tokenu w buildzie |

## 5. Co zmiana może zepsuć

| Zmieniany element | Konsumenci do aktualizacji | Dowód zgodności |
|---|---|---|
| etapy/QA | kontrakt, backend, symulator, UI | te same scenariusze i oczekiwany nextStage |
| schema raportu / JCS | Python, Java, TypeScript | identyczny hash fixture |
| EIP-712 | Solidity, Python, Java | jeden signer we wspólnym wektorze |
| ABI / events | wrapper Java, Viem, indexer | lokalny deployment i odczyt logów |
| OpenAPI | kontrolery, klient frontendowy, symulator | walidacja request/response |
| sieć/adres kontraktu | cała czwórka modułów | chainId i domena zgodne z deploymentem |
| migracje DB | API, worker i indexer | upgrade pustej i istniejącej bazy |
| status polecenia | API, worker, polling i UI QA | każdy status ma prezentację i regułę retry |
| certyfikat stacji / wystawca | rejestr backendu, symulator, administracja ról | zgodny signer, ważność, odwołanie i STATION_ROLE |
