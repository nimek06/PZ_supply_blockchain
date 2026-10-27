# Konfiguracja Spring

**Język:** Java 21; wartości z YAML/zmiennych środowiskowych.

Plan: `BlockchainProperties`, `IpfsProperties` (wybór Kubo/Pinata), `RelayerProperties`
(m.in. minimalny tip EIP-1559), `Web3jConfig` (kodowanie/podpis, bez `DefaultGasProvider`),
`HttpClientConfig`, konfiguracja Jackson/CORS i bezpieczeństwa.
Użyj `@ConfigurationProperties` z walidacją wymaganych wartości.

Weryfikuj zgodność RPC chainId i adresu kontraktu. Zdefiniuj timeouty,
liczbę potwierdzeń, okno ponownego skanu (N bloków), backoff i limity workerów. Nie dodawaj domyślnego klucza
lub adresu kontraktu, który udaje działającą konfigurację.

Konfiguracja testowa może jawnie podmieniać porty na fake; produkcyjna nie.
`VITE_*` nie jest źródłem sekretów backendu. CORS ogranicz do konfiguracji
dozwolonych originów. Actuator nie może ujawniać env/secrets publicznie.
Auth: SIWE tylko do wydania opaque Bearer tokenu (skrót SHA-256 w bazie); bez cookie, CSRF
i wymogu HTTPS dla API. Nie zostawiaj zapisu publicznego dla wygody.
