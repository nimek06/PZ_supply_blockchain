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
ani do czatu. Uzupełnij `contracts/.env` według [.env.example](../../../contracts/.env.example),
w tym klucz deployera i osobny testowy klucz stacji. Następnie z katalogu głównego repozytorium:

```bash
contracts/script/amoy-smoke.sh --dry-run  # bez transakcji; symuluje pierwszy potrzebny krok
contracts/script/amoy-smoke.sh            # prawdziwy smoke na Amoy
```

Szczegóły, wznawianie i wymagane saldo: [opis skryptu](../../../contracts/script/README.md#smoke-na-amoy-jednym-poleceniem).
W poprzednich ręcznych poleceniach brakowało ustawienia `SUPPLY_CHAIN_ADDRESS` po deployu.
Ponadto samo `--priority-gas-price` może dać tip wyższy niż wyliczone przez Forge
`maxFeePerGas`; wrapper ustawia obie wartości.

Po wykonaniu uzupełnij: adres kontraktu, hash transakcji `completeStage` (link do
`https://amoy.polygonscan.com/tx/<hash>`), ilość POL z faucetu, rzeczywisty tip w receipt.

| Pole | Wartość |
|---|---|
| Faucet (np. faucet.polygon.technology) | _do uzupełnienia_ |
| Adres kontraktu | _do uzupełnienia_ |
| Tx wdrożenia | _do uzupełnienia_ |
| Tx `completeStage` | _do uzupełnienia_ |
| Tip i koszt | _do uzupełnienia_ |
| Finalność i liczba potwierdzeń (DEC-07) | _do ustalenia po smoke_ |
