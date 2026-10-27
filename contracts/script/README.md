# Wdrożenie i role

**Język:** Solidity (`*.s.sol`), runner Forge Script; opcjonalnie shell jako wrapper.

Plan: `DeploySupplyChain.s.sol` do wdrożenia oraz `ConfigureRoles.s.sol`
do nadania ról. Wczytaj sieć, adresy i klucz podpisujący z konfiguracji
środowiska; sprawdź chainId przed broadcast. Nie wpisuj klucza w kodzie.

Najpierw dry run na Anvil, potem broadcast i sprawdzenie receipt, bytecode,
administratora oraz przyznanych ról. Nie nadaj `STATION_ROLE` relayerowi
tylko dlatego, że przesyła transakcje. Role QA należą do operatorów zmiany
uprawnionych do współzatwierdzenia kontroli, niezależnych od adresu stacji.
Rejestracji roli towarzyszy wystawienie/rejestracja certyfikatu off-chain;
skrypt kontraktu nie zapisuje pełnego certyfikatu w storage.

Skrypt wywołany ponownie ma sprawdzić istniejące role zamiast tworzyć
niezamierzone nowe wdrożenie. Nowy deployment wykonuj jawnie.
Zapisz publiczne wyniki w [deployments](../../shared/deployments/README.md),
eksportuj [ABI](../../shared/abi/README.md). Cache/broadcast pozostają lokalne.
Na Amoy sprawdź POL na gaz i prawidłowe RPC; nie używaj klucza produkcyjnego.
