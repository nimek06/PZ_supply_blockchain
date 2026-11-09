# Wdrożenie i role

**Język:** Solidity (`*.s.sol`), runner Forge Script; opcjonalnie shell jako wrapper.

Plan: `DeploySupplyChain.s.sol` do wdrożenia oraz `ConfigureRoles.s.sol`
do nadania ról. Wczytaj sieć, adresy i klucz podpisujący z konfiguracji
środowiska; sprawdź chainId przed broadcast. Nie wpisuj klucza w kodzie.

Najpierw dry run na Anvil, potem broadcast i sprawdzenie receipt, bytecode,
administratora oraz przyznanych ról. `STATION_ROLE` nie istnieje: stacje
rejestruj przez `registerStation(address, stationId, expiresAt, stageMask)`
(admin), nie nadawaj stacji ról relayera. Role QA (`QA_INSPECTOR_ROLE`) należą do
operatorów zmiany podpisujących `StageApproval`, niezależnych od adresu stacji.
Rejestracji stacji towarzyszy wystawienie certyfikatu JSON off-chain (poświadczenie
audytowe, musi zgadzać się z rejestrem); skrypt nie zapisuje certyfikatu w storage.
Opcjonalnie ustaw `setMaxDeadlineWindow` (robocza wartość 3600 s).

Skrypt wywołany ponownie ma sprawdzić istniejące role zamiast tworzyć
niezamierzone nowe wdrożenie. Nowy deployment wykonuj jawnie.
Zapisz publiczne wyniki w [deployments](../../shared/deployments/README.md),
eksportuj [ABI](../../shared/abi/README.md) (`forge inspect`). Cache/broadcast pozostają lokalne.
Na Amoy (smoke deploy już w tygodniu 1) sprawdź POL na gaz, prawidłowe RPC i jawnie
ustawiony `evm_version`; nie używaj klucza produkcyjnego.

## Lokalny deploy jednym poleceniem

`deploy-local.sh` przygotowuje Anvil (chainId 31337) dla backendu i symulatora. Wdraża
`SupplyChain` i nadaje role. Rejestruje stację ST-001 zgodnie z polem `registryCall`
w [certyfikacie testowym](../../shared/fixtures/station-certificate.json). Na końcu
eksportuje ABI i `shared/deployments/local.json`. Plik `local.json` opisuje bieżący
Anvil, więc git go ignoruje.

```bash
# Z katalogu głównego repozytorium; albo uruchom anvil w osobnym terminalu.
(cd infrastructure/local && docker compose up -d anvil)
contracts/script/deploy-local.sh                        # forge/cast z hosta
contracts/script/deploy-local.sh --docker               # forge/cast z obrazu Foundry w sieci Compose
```

Stan po skrypcie (publiczne klucze deweloperskie Anvila):

| Konto Anvil | Rola |
|---|---|
| #0 `0xf39F…2266` | `DEFAULT_ADMIN_ROLE`, wystawca certyfikatu testowego, domyślnie relayer |
| #1 `0x7099…79C8` | stacja ST-001: `stageMask` 15, `expiresAt` 1831536000 |
| #2 `0x3C44…93BC` | `QA_INSPECTOR_ROLE` (operator zmiany) |

Na czystym łańcuchu kontrakt trafia pod `0x5FbDB2315678afecb367f032d93F642f64180aa3`.
Tego adresu używają fixture'y i `application-local.yaml`. Skrypt kończy się linią
`Backend: CHAIN_ID=31337 CONTRACT_ADDRESS=…`. Inny relayer ustawisz zmienną
`LOCAL_RELAYER_ADDRESS`. Musi to być konto różne od stacji i od QA
([access-control](../../docs/access-control.md)).

**Ponowne uruchomienie.** Skrypt odnajduje ostatnie wdrożenie zapisane w `broadcast/`.
Sprawdza receipt, kod kontraktu i initcode zbudowany z bieżącego `src/`. Jeśli wszystko
się zgadza, tylko uzupełnia brakujące role i rejestrację stacji. Nowego kontraktu nie
tworzy.

**Kiedy skrypt odmawia:**
- chainId jest różny od 31337;
- kod w `src/` różni się od wdrożonego;
- konto #0 ma już transakcje, a `broadcast/` nie zna wdrożenia;
- relayer jest tym samym kontem co stacja albo QA.

**Nowy kontrakt** tworzysz tylko świadomie:
- `--fresh` – kontrakt dostaje nowy adres. Ustaw wtedy `CONTRACT_ADDRESS`. Podpisy
  z fixture'ów nie przejdą na tym kontrakcie.
- `docker compose restart anvil` – Anvil w Compose nie ma wolumenu, więc wraca czysty
  łańcuch i ten sam adres.

Ustawienia z `contracts/.env` (Amoy) nie trafiają do lokalnego deployu. Skrypt jawnie
ustawia wszystkie zmienne skryptów Forge.

**Tryb `--docker`.** `forge` i `cast` działają z obrazu `ghcr.io/foundry-rs/foundry:v1.8.6`
w sieci `pz-supply_default` (zmienna `COMPOSE_NETWORK`). Repozytorium jest zamontowane
pod tą samą ścieżką co na hoście. RPC to domyślnie `http://anvil:8545`. Na hoście
potrzebne są `jq` i `shasum`. Bez flagi skrypt sam wybiera obraz, gdy nie znajdzie
Foundry. Ten tryb sprawdzono tylko atrapą `docker` (te same wywołania), bez prawdziwego
demona. Na Linuksie z UID innym niż 1000 możliwe są problemy z uprawnieniami
do `out/`, `cache/` i `broadcast/`.

## Smoke na Amoy jednym poleceniem

`amoy-smoke.sh` przygotowuje kontrakt, rejestruje testową stację, tworzy partię i wykonuje
jej etap 0. Następnie eksportuje publiczne dane wdrożenia do `shared/deployments/amoy.json`
oraz wypisuje adres, transakcje, koszt i wynik oczekiwania na finalność.

Z katalogu głównego repozytorium:

```bash
# Tylko jeśli contracts/.env jeszcze nie istnieje:
cp contracts/.env.example contracts/.env
chmod 600 contracts/.env
# Uzupełnij lokalnie contracts/.env: DEPLOYER_PRIVATE_KEY i STATION_PRIVATE_KEY.
contracts/script/amoy-smoke.sh --dry-run
contracts/script/amoy-smoke.sh
```

Konto deployera wymaga testowych POL z faucetu. Stacja musi mieć osobny, wyłącznie
testowy klucz i nie potrzebuje środków; nowy klucz wygenerujesz przez `cast wallet new`.
Nie wysyłaj kluczy do czatu ani repozytorium. Puste `ADMIN_PRIVATE_KEY` i
`RELAYER_PRIVATE_KEY` oznaczają klucz deployera, a puste `STATION_ADDRESS` i `STATION_ID`
oznaczają odpowiednio adres klucza stacji i `keccak256("ST-001")`. Alternatywny plik
ustawień wskazuje `ENV_FILE`.

Przed wysłaniem skrypt sprawdza m.in. chainId, zgodność kluczy i adresów, rozdzielenie
stacji od relayera i QA, maskę etapu 0, deadline oraz saldo. Przy tipie 38,72 gwei
szacowany wymagany zapas na standardowy smoke wynosi około 0,085 POL; opłaty się
zmieniają i skrypt wypisuje aktualny szacunek. `--dry-run` wykonuje te kontrole i
symuluje **tylko pierwszy potrzebny krok**, bez wysyłania transakcji; nie potwierdza
powodzenia późniejszych kroków.

Domyślny tip to większa z wartości 30 gwei i `eth_maxPriorityFeePerGas` z RPC; ustawienie
`TIP_GWEI` pozwala go zmienić. Skrypt podaje Forge zarówno `--priority-gas-price`, jak i
`--with-gas-price` (`tip + 2 × base fee`). Samo ustawienie tipa może spowodować błąd,
gdy Forge wyliczy `maxFeePerGas` mniejsze od tipa.

Przy ponownym uruchomieniu skrypt sprawdza ostatnie wdrożenie z `broadcast/` i pomija
gotowe kroki. Jeżeli nie da się potwierdzić zapisanego wdrożenia, zatrzymuje się zamiast
tworzyć nowy kontrakt. Zachowaj lokalny `broadcast/`; nowe wdrożenie wymusza `--fresh`.
Jeżeli tworzenie partii przeszło, ale etap 0 nie, sprawdź receipt i ustaw nowy
`BATCH_NUMBER` przed ponowieniem. Po innym częściowym błędzie sprawdź stan i role przed
ponowieniem.

Po nowym `completeStage` skrypt czeka na tag `finalized` maksymalnie 120 sekund i
sprawdza hash bloku. Przekroczenie czasu oznacza brak potwierdzenia finalności, nie
jej niepowodzenie; zmiana hasha przerywa skrypt. Uzupełnij
[notatkę Amoy](../../docs/weekly_plans/week1/adam-amoy-notes.md) prawdziwymi wynikami.
`amoy.json` zawiera dane publiczne; klucze, URL z tokenem, `broadcast/` i `cache/`
pozostają lokalne.
