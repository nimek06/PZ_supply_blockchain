# Porty integracyjne

**Język:** Java 21, interfejsy.

Przewidywane porty: `ReportStorage`, `ChainGateway`, `CommandRepository`,
`BatchProjectionRepository` i `StationSignatureVerifier`.
Twórz je przy pierwszej implementacji przypadku użycia, nie na zapas.
`ReportStorage` ma adaptery Kubo i Pinata. Wysyła dokładnie bajty JCS; wynik to CID (CIDv0/CIDv1).
`ChainGateway` rozdziela submit, lookup receipt, odczyt nonce relayera, roli/rejestru stacji i logów,
zamiast jednej metody zwracającej zawsze sukces.

Argumenty i wyniki to typy domenowe/aplikacyjne, nie Web3j Response,
RestClient ani encje JPA. Określ: błędy, idempotencję, granice timeout,
czy operacja może być ponowiona i co oznacza brak receipt.

Implementacje należą do infrastructure; konfiguracja łączy beany.
Test fake nie powinien być produkcyjnym fallbackiem przy braku adaptera.

Weryfikacja certyfikowanej tożsamości wymaga portu weryfikatora certyfikatów
(osoba 1) przy pierwszym użyciu; nie łącz jej z klientem IPFS.
Wynik ma odróżniać nieznany certyfikat, zły podpis wystawcy, wygaśnięcie,
odwołanie, adres niezgodny z podpisem raportu oraz niezgodność z rejestrem on-chain.
