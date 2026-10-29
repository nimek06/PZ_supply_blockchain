# Tydzień 1 - notatka Adama: smoke test Amoy

**Data:** 2026-10-28. **Sieć:** Polygon Amoy, chainId `80002`. **Narzędzia:** Foundry 1.8.6.

## Co sprawdzono bez portfela (publiczny RPC)

| Pytanie | Wynik |
|---|---|
| `evm_version` | `cancun` działa: symulowany deploy (`eth_call` create) zwraca pełny runtime; `PUSH0`, `MCOPY`, `TSTORE`/`TLOAD` wykonują się (kontrolny `INVALID` jest odrzucany). Nie trzeba schodzić do `shanghai`/`paris`. |
| Gaz wdrożenia | `eth_estimateGas` dla deployu `SupplyChain`: ok. **1 470 000** gazu. |
| Minimalny tip | `eth_maxPriorityFeePerGas` = **30 gwei** (30 000 000 000 wei), base fee ok. 63 wei. Relayer ustawia `maxPriorityFeePerGas >= 30 gwei`; niższy tip grozi zawieszeniem transakcji. |
| Koszt deployu | ok. 1,47 M × 30 gwei ≈ **0,044 POL** (+ rejestracja stacji, partia i etap ≈ 0,01 POL). |
| `eth_getLogs` | `polygon-amoy-bor-rpc.publicnode.com`: maks. **10 000 bloków** na zapytanie. `polygon-amoy.gateway.tenderly.co`: 100 000 bloków przeszło. `polygon-amoy.drpc.org` (free): odrzuca już 1 000 bloków („ranges over 10000 blocks are not supported on free plan”). `rpc.ankr.com/polygon_amoy`: wymaga klucza API. Indexer (tyg. 4-5) powinien skanować w oknach ≤ 10 000 bloków. |
| RPC | `rpc-amoy.polygon.technology` nie rozwiązywał się w DNS w chwili testu; działające: publicnode, tenderly, drpc. |

## Do wykonania z portfelem (wymaga POL z faucetu)

Prawdziwe transakcje wymagają testowego konta z POL; klucz nie może trafić do repozytorium
ani do czatu. Kroki (z `contracts/`, zmienne w `contracts/.env`, wzór: [.env.example](../../../contracts/.env.example)):

```bash
# 1. Deploy (admin = relayer = konto testowe z POL)
forge script script/DeploySupplyChain.s.sol --rpc-url amoy --broadcast --slow \
  --priority-gas-price 30gwei
# 2. Rejestracja stacji (adres testowego klucza stacji, bez środków)
forge script script/RegisterStation.s.sol --rpc-url amoy --broadcast --slow --priority-gas-price 30gwei
# 3. Partia + completeStage etapu 0
forge script script/SmokeStage.s.sol --rpc-url amoy --broadcast --slow --priority-gas-price 30gwei
# 4. Publiczne dane wdrożenia
script/export-deployment.sh 80002 amoy
```

Po wykonaniu uzupełnij: adres kontraktu, hash transakcji `completeStage` (link do
`https://amoy.polygonscan.com/tx/<hash>`), ilość POL z faucetu, rzeczywisty tip w receipt.

| Pole | Wartość |
|---|---|
| Faucet (np. faucet.polygon.technology) | _do uzupełnienia_ |
| Adres kontraktu | _do uzupełnienia_ |
| Tx `completeStage` | _do uzupełnienia_ |
| Liczba potwierdzeń (DEC-11) | _do ustalenia po smoke_ |
