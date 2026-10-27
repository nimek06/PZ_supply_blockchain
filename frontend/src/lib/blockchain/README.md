# Portfele i kontrakt

**Język:** TypeScript; Wagmi v2, Viem, RainbowKit.

Plan: `chains.ts`, `wagmiConfig.ts`, `contract.ts`.
Użyj poprawnej sieci lokalnej i Amoy, publicznej konfiguracji adresu,
ABI z shared/abi oraz typowanych argumentów.
Big integers przekaż jako `bigint`, nie `number`.

Czytanie i zapisy rozdziel; publiczny paszport nie musi łączyć wallet.
Przed zapisem sprawdź sieć/adres i zasymuluj wywołanie, jeśli dostępne.
Obsłuż rejection, revert i oczekiwanie na receipt/potwierdzenia.
Zmiana konta/sieci musi unieważnić odpowiednie dane uprawnień.

Tu nie ma kluczy prywatnych; podpis wykonuje provider portfela.
Prywatny RPC token nie może trafić do builda pod pozorem konfiguracji.
Nie ręcznie odtwarzaj ABI, gdy zmienił się kontrakt.
