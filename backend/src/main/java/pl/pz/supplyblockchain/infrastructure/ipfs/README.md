# IPFS i Pinata

**Język:** Java 21; Spring RestClient.

Plan: `PinataReportStorage`, model odpowiedzi API i kontrolowana kanonizacja
raportu zgodna z protokołem. Pinowanie przez API Pinaty, np.
`/pinning/pinJSONToIPFS`, zgodnie z wybranym wariantem i bieżącą dokumentacją konta.
Waliduj status HTTP i obecność prawidłowego `IpfsHash`; pusty CID jest błędem.

Nie dodawaj do raportu timestampów backendu ani metadanych zmieniających
podpisaną treść. CID zapisz trwale przed zleceniem blockchaina.
Timeout pinowania może oznaczać, że usługa wykonała operację: retry ma być
kontrolowany, a wielokrotne pinowanie nie może tworzyć kolejnych stage commands.

JWT z konfiguracji, nigdy z kodu. Ustal connect/read timeout, limity danych
i jawne błędy. Testuj 401, rate limit, timeout, 5xx, niepoprawny JSON i brak CID.
Przy odczycie raportu weryfikuj jego schemat i hash, nie sam status HTTP.
