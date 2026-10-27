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
