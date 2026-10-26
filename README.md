# PZ_supply_blockchain
1. Smart Contracts & Blockchain (Osoba 1)
Logika maszyny stanów partii, walidacja kolejności etapów, bramki kontroli jakości (QA) oraz przypisanie uprawnień (RBAC).
- Język: Solidity (0.8.24+)
- Narzędzia lokalne i testowe: Hardhat lub Foundry (Anvil)
  - Rekomendacja: Nawet jeśli backend jest w Javie, kontrakt najwygodniej pisać i kompilować lokalnie za pomocą Foundry (błyskawiczne testy jednostkowe) lub Hardhat (generuje folder artifacts/, z którego Java łatwo pobierze pliki .abi i .bin).
- Lokalny węzeł: Anvil lub wbudowany Hardhat Network (lokalna sieć stawiająca się w sekundę do szybkich testów integracyjnych z Javą).
- Sieć docelowa: Polygon Amoy Testnet (oficjalny testnet Polygon bazujący na Ethereum Sepolia, zerowe koszty testów, darmowy kranik monet testowych POL).
- Biblioteki kontraktowe:
  - OpenZeppelin Contracts:
    - AccessControl.sol – zarządzanie rolami (np. STATION_ROLE, QA_INSPECTOR_ROLE, ADMIN_ROLE).
    - ECDSA.sol / MessageHashUtils.sol – do kryptograficznej weryfikacji podpisów przesyłanych ze stacji.
2. Backend, Relayer & Storage (Osoba 2)
Centralny punkt logiki biznesowej, bezpieczny relayer transakcji blockchainowych oraz integracja z rozproszonym magazynem danych IPFS.
- Język i Framework: Java 21 (LTS) + Spring Boot 3.3+
- Integracja z Blockchainem: Web3j (org.web3j:core)
  - Użyj Web3j CLI lub pluginu Gradle/Maven (web3j-maven-plugin), aby wygenerować klasy Javowe bezpośrednio z plików ABI kontraktu. Dzięki temu wywołanie metody kontraktu w Javie wygląda jak zwykła metoda: myContract.advanceStage(batchId, ipfsCid, signature).send().
  - Zarządzanie kontem/Relayerem: RawTransactionManager lub FastRawTransactionManager z Web3j (pozwala na podpisywanie transakcji kluczem prywatnym serwera i wysyłanie ich na Polygon).
- Składowanie danych (IPFS):
  - Pinata Cloud API z wykorzystaniem Spring RestClient lub WebClient.
  - Backend odbiera pomiary, pakuje je do ustrukturyzowanego JSON-a, wysyła żądaniem HTTP POST (Multipart/JSON) do endpointu /pinning/pinJSONToIPFS Pinaty i odbiera skrót IpfsHash (CID).
- Baza danych (Cache & Event Indexer): PostgreSQL + Spring Data JPA
  - Zapisuje bieżący cache stanu partii, historię transakcji relayera i logi błędów.
- Kolejkowanie transakcji: Zwykła asynchroniczna kolejka (np. @Async / Java BlockingQueue lub prosty lightweight Redis / RabbitMQ).
  - Dlaczego: Jeśli dwie stacje zgłoszą ukończenie etapu w tej samej sekundzie, relayer w Javie musi wysłać transakcje z kolejnymi numerami nonce, unikając błędu nonce collision.
3. Symulator IoT i Stacji Montażowych (Osoba 4)
Samodzielny skrypt/serwis udający fizyczne gniazda produkcyjne i sterowniki maszyn (PLC/sensory).
- Język: Python 3.11+
- Biblioteki kluczowe:
  - eth-account: Generowanie par kluczy kryptograficznych (adres Ethereum + klucz prywatny) dla każdego stanowiska oraz podpisywanie wiadomości zgodnie ze standardem EIP-712 (strukturyzowane dane JSON).
  - pydantic: Definiowanie ścisłych modeli danych telemetrycznych (np. temperature: float, torque_nm: float, qa_passed: bool, timestamp: int).
  - faker / random: Generowanie realistycznych danych produkcyjnych (z kontrolowaną możliwością wywołania anomalii na potrzeby testów odrzucania przez QA).
  - httpx lub requests: Wysyłanie podpisanych pakietów telemetrycznych HTTP POST do backendu w Javie.
- Struktura symulacji:
  - Możliwość uruchomienia w trybie pętli (automatyczna linia montażowa) lub w trybie interaktywnym (ręczne wyzwalanie kolejnych kroków i testów negatywnych).
4. Frontend & Panel Operatora / Paszport Produktu (Osoba 3)
Aplikacja SPA/PWA pozwalająca operatorom na hali montażowej rejestrować zdarzenia oraz oferująca publiczny portal weryfikacji partii.
- Framework: React 18/19 (Vite + TypeScript)
- Komunikacja z Blockchainem i Portfelami:
  - Wagmi v2 + Viem: Najnowocześniejsze i najbardziej stabilne biblioteki do łączenia Reacta z EVM (Polygon).
  - RainbowKit (lub AppKit / dawniej Web3Modal): Gotowy komponent logowania portfelem (MetaMask / Coinbase Wallet) dla uprawnionych kierowników jakości (QA).
- UI & Style: Tailwind CSS + shadcn/ui
  - Gotowe, nowoczesne komponenty interfejsu (karty, statusy linii produkcyjnej, tabelki historii, alerty błędów).
- Obsługa kodów QR (Kamera & Generowanie):
  - @html5-qrcode: Obsługa skanowania etykiet partii bezpośrednio z kamery telefonu/tabletu w widoku operatora.
  - qrcode.react: Dynamiczne generowanie kodu QR dla partii gotowej do przekazania dalej.
- Komunikacja z Backendem: TanStack Query (React Query) + Axios do pobierania zbuforowanych danych, raportów i statusów z API Spring Boota.
- Wizualizacja osi czasu: Własny komponent osi czasu (Timeline) prezentujący każdy krok (wraz z linkiem do IPFS Gateway oraz Polygonscan).
Przebieg pojedynczego zdarzenia produkcyjnego (End-to-End)
1. Stacja montażowa (Python): Kończy operację. Tworzy JSON z pomiarami (np. ciśnienie 6.2 bar, status: PASS). Podpisuje obiekt kluczem stacji i wysyła do Javy:POST /api/v1/batches/{batchId}/stages
2. Backend (Java / Spring):
  - Odbiera żądanie, sprawdza poprawność schematu.
  - Wysyła pełny JSON do Pinata IPFS i otrzymuje hash, np. QmXoypizjW3WknFiJnKLwHCnL72vedxjQkDDP1mXWo6uco.
  - Przez Web3j tworzy i podpisuje transakcję wywołującą smart kontrakt:contract.completeStage(batchId, "QmXoy...", signature).send().
3. Smart Kontrakt (Solidity na Polygon):
  - Sprawdza, czy podpis pochodzi od zarejestrowanej stacji (ecrecover).
  - Sprawdza, czy poprzedni krok został zaliczony (maszyna stanów).
  - Zapisuje CID w tablicy historii partii i emituje zdarzenie StageCompleted(batchId, stageId, ipfsCid).
4. Interfejs (React):
  - Operator widzi natychmiastową zmianę statusu na zielony.
  - Publiczny użytkownik po wpisaniu ID partii widzi pełną oś czasu, a klikając w dany etap, aplikacja pobiera oryginalny raport pomiarowy bezpośrednio z bramki IPFS.
