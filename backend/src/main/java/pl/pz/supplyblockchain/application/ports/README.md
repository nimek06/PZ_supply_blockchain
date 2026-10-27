# Porty integracyjne

**Język:** Java 21, interfejsy.

Przewidywane porty: `ReportStorage`, `ChainGateway`, `CommandRepository`,
`BatchProjectionRepository` i `StationSignatureVerifier`.
Twórz je przy pierwszej implementacji przypadku użycia, nie na zapas.
`ChainGateway` rozdziela submit, lookup receipt, odczyt nonce/roli i logów,
zamiast jednej metody zwracającej zawsze sukces.

Argumenty i wyniki to typy domenowe/aplikacyjne, nie Web3j Response,
RestClient ani encje JPA. Określ: błędy, idempotencję, granice timeout,
czy operacja może być ponowiona i co oznacza brak receipt.

Implementacje należą do infrastructure; konfiguracja łączy beany.
Test fake nie powinien być produkcyjnym fallbackiem przy braku adaptera.

Weryfikacja certyfikowanej tożsamości wymaga portu rejestru/weryfikatora
certyfikatów przy pierwszym użyciu; nie łącz jej z klientem Pinaty.
Wynik ma odróżniać nieznany certyfikat, zły podpis wystawcy, wygaśnięcie,
odwołanie i adres niezgodny z podpisem raportu.
