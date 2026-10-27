# Konfiguracja Spring

**Język:** Java 21; wartości z YAML/zmiennych środowiskowych.

Plan: `BlockchainProperties`, `IpfsProperties`, `RelayerProperties`,
`Web3jConfig`, `HttpClientConfig`, konfiguracja Jackson/CORS i bezpieczeństwa.
Użyj `@ConfigurationProperties` z walidacją wymaganych wartości.

Weryfikuj zgodność RPC chainId i adresu kontraktu. Zdefiniuj timeouty,
liczbę potwierdzeń, backoff i limity workerów. Nie dodawaj domyślnego klucza
lub adresu kontraktu, który udaje działającą konfigurację.

Konfiguracja testowa może jawnie podmieniać porty na fake; produkcyjna nie.
`VITE_*` nie jest źródłem sekretów backendu. CORS ogranicz do konfiguracji
dozwolonych originów. Actuator nie może ujawniać env/secrets publicznie.
Sposób auth uzgodnij z zespołem; nie zostawiaj zapisu publicznego dla wygody.
