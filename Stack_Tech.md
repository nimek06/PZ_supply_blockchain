# PZ_supply_blockchain
### 1. Smart Contracts & Blockchain 
Logika maszyny stanów partii, walidacja kolejności etapów, bramki kontroli jakości (QA) oraz przypisanie uprawnień (RBAC).
- Język: Solidity (0.8.24+)
- Narzędzia lokalne i testowe: Foundry (forge, Anvil). Hardhat nie jest wybrany.
  - Backend jest w Javie; kontrakty są kompilowane i testowane Foundry. ABI i bytecode eksportuj do `shared/abi` przez `forge inspect <Contract> abi` / `bytecode`.
  - `evm_version` ustaw jawnie w `foundry.toml` (zgodność z Amoy; zweryfikuj).
- Lokalny węzeł: Anvil (lokalna sieć stawiająca się w sekundę; test reorg przez `evm_snapshot`/`evm_revert`).
- Sieć docelowa: Polygon Amoy Testnet (oficjalny testnet Polygon bazujący na Ethereum Sepolia, zerowe koszty testów, darmowy kranik monet testowych POL).
- Biblioteki kontraktowe:
  - OpenZeppelin Contracts:
    - AccessControl.sol – zarządzanie rolami (DEFAULT_ADMIN_ROLE, RELAYER_ROLE, QA_INSPECTOR_ROLE; `STATION_ROLE` usunięta - stacje w rejestrze on-chain: `registerStation`/`revokeStation`, `stageMask`, `expiresAt`).
    - EIP712.sol / ECDSA.sol – weryfikacja dwóch podpisów EIP-712 w `completeStage`: stacji (`StageCompletion`, bez nonce) i operatora QA (`StageApproval`, poza łańcuchem, bez transakcji `approveStage`).
### 2. Backend, Relayer & Storage - Mikołaj
Centralny punkt logiki biznesowej, bezpieczny relayer transakcji blockchainowych oraz integracja z rozproszonym magazynem danych IPFS.
- Język i Framework: Java 21 (LTS) + aktualna wspierana wersja Spring Boot (wybierz przy inicjalizacji; sprawdź zgodność z Web3j i Jackson)
- Integracja z Blockchainem: Web3j (org.web3j:core)
  - Web3j służy tylko do kodowania wywołań (z ABI) i podpisu transakcji. Nie używaj `.send()` z wygenerowanego wrappera ani `DefaultGasProvider`.
  - Relayer: własne podpisywanie transakcji kluczem serwera, własny dostawca opłat EIP-1559 z konfigurowalnym minimalnym tipem, zapis podpisanej transakcji przed wysłaniem; bez fee-bump w MVP.
  - EIP-712 w Javie: ręczna implementacja jednej stałej struktury (Keccak + odzyskanie adresu), sprawdzona wspólnym wektorem (Python, Java, TS, Solidity).
  - Backend wydaje też token po SIWE (EIP-4361): opaque Bearer, w bazie skrót SHA-256; bez cookie i CSRF.
- Składowanie danych (IPFS):
  - Port `ReportStorage`: Kubo (lokalnie i w CI) oraz Pinata (demo), przez Spring RestClient lub WebClient.
  - Backend odbiera pomiary, kanonizuje je (JCS) i wysyła dokładnie te bajty jako plik (multipart) do Kubo/Pinaty, po czym odbiera CID (przyjmuj CIDv0 i CIDv1). Endpoint i format odpowiedzi Pinaty zweryfikuj w aktualnej dokumentacji (stary `/pinning/pinJSONToIPFS` jest legacy); sprawdź limity planu.
- Baza danych (Cache & Event Indexer): PostgreSQL + Spring Data JPA
  - Zapisuje bieżący cache stanu partii, polecenia, podpisane transakcje relayera, `qa_approvals` i logi błędów.
  - Indexer: głębokość potwierdzeń + okno ponownego skanu ostatnich N bloków (porównanie block hash).
- Kolejkowanie transakcji: trwała kolejka w PostgreSQL (nie `@Async`/`BlockingQueue`, nie Redis/RabbitMQ w MVP).
  - Dlaczego: jeden relayer musi wysyłać transakcje z kolejnymi numerami nonce konta relayera (nie stacji; `StageCompletion` nie ma nonce) i przetrwać restart.
### 3. Symulator IoT i Stacji Montażowych
Samodzielny skrypt/serwis udający fizyczne gniazda produkcyjne i sterowniki maszyn (PLC/sensory).
- Język: Python 3.11-3.12 (venv)
- Biblioteki kluczowe:
  - eth-account: Generowanie par kluczy kryptograficznych (adres Ethereum + klucz prywatny) dla każdego stanowiska oraz podpisywanie `StageCompletion` zgodnie z EIP-712. Drugi, niezależny klucz (operator QA) podpisuje `StageApproval` przy kontroli.
  - pydantic: Definiowanie ścisłych modeli danych telemetrycznych (np. temperature: float, torque_nm: float, qa_passed: bool, timestamp: int).
  - faker / random: Generowanie realistycznych danych produkcyjnych (z kontrolowaną możliwością wywołania anomalii na potrzeby testów odrzucania przez QA).
  - httpx lub requests: Wysyłanie podpisanych pakietów telemetrycznych HTTP POST do backendu w Javie.
- Struktura symulacji:
  - Możliwość uruchomienia w trybie pętli (automatyczna linia montażowa) lub w trybie interaktywnym (ręczne wyzwalanie kolejnych kroków i testów negatywnych).
### 4. Frontend & Panel Operatora / Paszport Produktu
Aplikacja SPA (bez PWA w MVP) pozwalająca operatorom na hali montażowej rejestrować zdarzenia oraz oferująca publiczny portal weryfikacji partii.
- Framework: React 18/19 (Vite + TypeScript)
- Komunikacja z Blockchainem i Portfelami:
  - Wagmi v2 + Viem: Najnowocześniejsze i najbardziej stabilne biblioteki do łączenia Reacta z EVM (Polygon).
  - RainbowKit (lub AppKit / dawniej Web3Modal): logowanie portfelem (SIWE) i podpis `StageApproval` przez operatora QA. RainbowKit wymaga `projectId` WalletConnect/Reown.
  - Skaner QR: biblioteka wybrana po spike'u (`html5-qrcode` słabo utrzymywana; kandydaci: `qr-scanner`, `@yudiel/react-qr-scanner`). Kamera wymaga HTTPS - lokalny HTTPS dla frontendu (certyfikat deweloperski lub tunel).
- UI & Style: Tailwind CSS + shadcn/ui
  - Gotowe, nowoczesne komponenty interfejsu (karty, statusy linii produkcyjnej, tabelki historii, alerty błędów).
- Komunikacja z Backendem: TanStack Query (React Query) + Axios do pobierania zbuforowanych danych, raportów i statusów z API Spring Boota.
- Wizualizacja osi czasu: Własny komponent osi czasu (Timeline) prezentujący każdy krok (wraz z linkiem do IPFS Gateway oraz Polygonscan).
Przebieg pojedynczego zdarzenia produkcyjnego (End-to-End)
1. Stacja montażowa (Python): Kończy operację. Tworzy JSON z pomiarami (np. ciśnienie 6.2 bar, status: PASS). Podpisuje `StageCompletion` (EIP-712, bez nonce) kluczem stacji i wysyła do Javy: POST /api/v1/batches/{batchId}/stages (partia ma numer `B-RRRR-NNNN`, `batchId = keccak256(utf8(batchNumber))`; szczegóły w docs/protocol.md)
2. Backend (Java / Spring):
  - Odbiera żądanie, sprawdza poprawność schematu.
  - Wysyła bajty JCS raportu przez `ReportStorage` (Kubo/Pinata) i otrzymuje CID (CIDv0 lub CIDv1, np. `bafy...`).
  - Dla kontroli zbiera podpis `StageApproval` operatora QA (tabela `qa_approvals`).
  - Przez Web3j koduje wywołanie `completeStage(batchId, stageId, reportHash, qaPassed, deadline, ipfsCid, stationSignature, approvalDeadline, approvalSignature)`, podpisuje je kluczem relayera i wysyła własnym kodem (zapis podpisanej transakcji w PostgreSQL).
3. Smart Kontrakt (Solidity na Polygon):
  - Sprawdza, czy podpis pochodzi z aktywnej, niewygasłej stacji w rejestrze on-chain, której `stageMask` obejmuje etap (ECDSA/EIP-712).
  - Sprawdza kolejność etapów (`stageId == nextStage`) i `deadline`; przy kontroli weryfikuje podpis operatora QA (rola, adres różny od stacji).
  - `qaPassed=true`: przesuwa partię i emituje `StageCompleted(batchId, stageId, station, qaOperator, reportHash, ipfsCid)`. `qaPassed=false`: emituje `StageRejected` bez zmiany stanu partii (backend: 202, `outcome=REJECTED`).
4. Interfejs (React):
  - Operator widzi natychmiastową zmianę statusu na zielony.
  - Publiczny użytkownik po wpisaniu ID partii widzi pełną oś czasu, a klikając w dany etap, aplikacja pobiera oryginalny raport pomiarowy bezpośrednio z bramki IPFS.
