# Lokalne środowisko

**Języki:** YAML, `.env` z konfiguracją, opcjonalnie shell.

Planowane pliki: `compose.yaml` i `.env.example` bez wartości sekretów.
Compose uruchamia PostgreSQL, Anvil oraz Kubo (lokalny IPFS) z przypiętymi obrazami.
Początkowo API, Vite i symulator uruchamiaj lokalnie w osobnych terminalach.

Proponowane porty hosta: PostgreSQL 5432, Anvil RPC 8545, backend 8080,
Vite 5173 (przez HTTPS - kamera/QR wymaga bezpiecznego kontekstu; certyfikat deweloperski
lub tunel), Kubo API 5001 / gateway 8081 (do weryfikacji). Przed użyciem sprawdź kolizje; adresy konfiguruj, nie wpisuj na stałe
w klientach. Sieć lokalna nie jest Amoy.

Użyj healthcheck PostgreSQL i sprawdzenia RPC (`eth_chainId`) dla Anvil.
Kolejność: baza i node -> migracje -> deployment -> role -> konfiguracja API
-> API -> frontend/symulator. `depends_on` nie zastępuje gotowości aplikacji.

Po dodaniu Compose: `docker compose up -d`, `docker compose ps`,
sprawdzenie health/RPC, `docker compose logs`, a na koniec `docker compose down`.
Nie usuwaj wolumenów automatycznie. Reset danych ma wymagać świadomej decyzji.

Pinata (tylko demo) pozostaje usługą zewnętrzną; lokalnie i w testach używaj Kubo lub stubów bez tokenów.
Nie wystawiaj lokalnych kluczy Anvil ani bazy do publicznej sieci.
Robocze `data/` i `secrets/` są ignorowane i nie są katalogami kodu.

W przygotowaniu demonstracji wystaw certyfikat JSON każdej stacji, zarejestruj ją w rejestrze
on-chain (`registerStation`) i zapewnij osobne konto operatora zmiany (`QA_INSPECTOR_ROLE`)
do podpisu `StageApproval`.
Wariant certyfikatu określa [station-identity](../../docs/station-identity.md).
Nie budujemy fizycznej linii ani rozbudowanego PKI; cel to programowy MVP z PDF.
