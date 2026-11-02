# Web3j EIP-1559 Spike — Week 1 Notes

## Library

**Web3j 6.1.0** (`org.web3j:core`). Compatible with Spring Boot 4.1.2
(Jackson 3.1.7 managed by Boot; Web3j compiled against 3.2.3 but works at runtime).

## Call Encoding

`FunctionEncoder.encode(new Function("completeStage", inputs, emptyList()))` produces
the ABI-encoded calldata including the 4-byte selector. No generated wrapper needed.

See `CompleteStageCallEncoder.java`.

## Transaction Signing

`RawTransaction.createTransaction(chainId, nonce, gasLimit, maxPriorityFeePerGas,
maxFeePerGas, to, value, data, accessList, authorizationList)` builds the unsigned
EIP-1559 envelope. `TransactionEncoder.signMessage(rawTx, credentials)` signs it.
The raw bytes can be stored in DB before sending — the plan requires this so that
after a restart we can re-send the exact same bytes and reconcile by hash.

See `Eip1559TransactionSigner.java`.

## Fee Calculation

```
baseFee     = ethGetBlockByNumber(LATEST).baseFeePerGas
priorityFee = ethMaxPriorityFeePerGas()
maxFee      = baseFee * 2 + priorityFee
gasLimit    = ethEstimateGas(...) * 1.2
```

**Polygon tip behavior:** Polygon PoS requires a minimum priority fee
(typically 25-30 gwei) or the transaction will be stuck. The relayer config
must expose `supplychain.relayer.min-tip-gwei` (default 30 for Polygon,
1 for Anvil/Amoy). At runtime: `priorityFee = max(rpcTip, minTipGwei * 1e9)`.
This is not yet wired into config properties but the spike code in
`Web3jRelayerSpike.java` shows the raw RPC calls.

## Sending

`web3j.ethSendRawTransaction(signedHex).send().getTransactionHash()` — low-level
JSON-RPC, not the wrapper's `.send()`. No `DefaultGasProvider` anywhere.

## What's Deferred

- Fee-bump / replacement: out of MVP scope (manual procedure for stuck tx).
- Actual Anvil integration test: requires Docker / Foundry, not available in CI yet.
  The encoder and signer are unit-tested; the spike class is compile-verified.
