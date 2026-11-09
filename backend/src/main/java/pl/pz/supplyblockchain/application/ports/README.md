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

Weryfikację certyfikowanej tożsamości (T010, osoba 1) opisują `StationCertificateVerifier`
i `StationRegistry` (odczyt `getStation`); nie łącz jej z klientem IPFS.
Wynik `StationIdentityVerification` odróżnia: zły format, niezgodny `payloadHash`,
zły podpis wystawcy, niezaufanego wystawcę, inne wdrożenie, ważność (`NOT_YET_VALID`, `EXPIRED`),
brak rejestracji (`PENDING_ACTIVATION`), odwołanie, niezgodność z rejestrem on-chain,
adres niezgodny z podpisem raportu i etap spoza `allowedStageIds`.
Nieznany certyfikat (brak zapisanego certyfikatu dla adresu) rozpoznaje wywołujący przed weryfikacją.
Błąd odczytu rejestru to `StationRegistryException` (`DEPENDENCY_UNAVAILABLE`), a nie werdykt o stacji.
