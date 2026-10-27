# Integracje klienta

**Język:** TypeScript.

- [api](api/README.md): Axios, DTO i wywołania backendu.
- [blockchain](blockchain/README.md): sieci, ABI, Wagmi/Viem i portfele.

Wspólne sprawdzanie konfiguracji może być w `config.ts`.
Nie wrzucaj tutaj komponentów ani reguł UI. Helper musi mieć konkretną
odpowiedzialność, nie nieokreślony plik z dziesiątkami utils.
Wszystkie wartości `VITE_*` są publiczne w buildzie.
