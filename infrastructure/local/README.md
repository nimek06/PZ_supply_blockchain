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

## Uruchomienie i reset środowiska (T007)

```bash
cd infrastructure/local
cp .env.example .env            # uzupełnij POSTGRES_PASSWORD (puste hasło przerywa start)
docker compose up -d
docker compose ps               # po ok. 30 s: postgres, anvil i kubo mają status "healthy"
docker compose logs -f anvil    # logi jednej usługi
docker compose down             # zatrzymuje usługi, ZACHOWUJE dane (wolumeny)
```

Health checki: PostgreSQL (`pg_isready`), Anvil (`cast chain-id` = 31337), Kubo (`ipfs id`).
Ręcznie: `cast chain-id --rpc-url http://127.0.0.1:8545`, `curl -X POST http://127.0.0.1:5001/api/v0/id`.

### Reset środowiska - tylko świadomie

**Anvil nie ma wolumenu**: każdy restart (`down`/`up`, restart kontenera, restart Dockera) daje czysty
łańcuch od bloku 0, a kontrakt, role, stacje i partie znikają. **PostgreSQL zostaje**, więc po restarcie
Anvila baza opisuje łańcuch, którego już nie ma (checkpoint indexera wskazuje nieistniejące bloki,
polecenia mają `txHash` z poprzedniego łańcucha). Nic nie czyści się automatycznie.

Po restarcie Anvila wybierz jedno:

1. **Pełny reset (zalecany w developmencie):** `docker compose down -v` usuwa wszystkie wolumeny
   (PostgreSQL i Kubo), potem `up -d`, wdrożenie kontraktu (`contracts/script/`), rejestracja stacji i ról.
2. **Reset tylko łańcucha i bazy, bez IPFS:** `docker compose down`, `docker volume rm pz-supply_postgres-data`,
   `docker compose up -d`. Kubo zachowuje pliki; CID są deterministyczne, więc ponowne dodanie nie szkodzi.

`down -v` jest **nieodwracalne**: kasuje bazę z poleceniami i podpisanymi transakcjami. Nie używaj go
na środowisku, którego dane mają znaczenie (Amoy/demo nie korzysta z tego Compose).
Po resecie adres kontraktu na czystym Anvilu jest taki sam (`0x5FbDB...aa3` przy kluczu deployera Anvil
i nonce 0), ale wartości w `shared/deployments/` odnawiaj skryptem `export-deployment.sh`.

### Bezpieczeństwo

Porty są wystawione wyłącznie na `127.0.0.1`. API Kubo (5001) i RPC Anvil nie mają uwierzytelnienia,
a Anvil używa publicznie znanych kluczy: nie otwieraj ich w sieci i nie używaj tych kluczy poza lokalnym łańcuchem.
