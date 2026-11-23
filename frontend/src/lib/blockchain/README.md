# Portfele i kontrakt

**Język:** TypeScript; Wagmi v3, Viem.

Plan: `chains.ts`, `wagmiConfig.ts` (konektor `injected`), `contract.ts`, `typedData.ts`.
Użyj sieci lokalnej (Amoy poza aplikacją, DEC-17), publicznej konfiguracji adresu,
ABI z shared/abi oraz typowanych argumentów.
Big integers przekaż jako `bigint`, nie `number`.

Czytanie i zapisy rozdziel; publiczny paszport nie musi łączyć wallet.
Przed zapisem sprawdź sieć/adres i zasymuluj wywołanie, jeśli dostępne.
Obsłuż rejection, revert i oczekiwanie na receipt/potwierdzenia.
Zmiana konta/sieci musi unieważnić odpowiednie dane uprawnień.

Tu nie ma kluczy prywatnych; podpis (SIWE, `StageApproval`) wykonuje provider portfela.
Typy EIP-712 sprawdź wspólnym wektorem z shared/fixtures (TS/viem).
Prywatny RPC token nie może trafić do builda pod pozorem konfiguracji.
Nie ręcznie odtwarzaj ABI, gdy zmienił się kontrakt.
