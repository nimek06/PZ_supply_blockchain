# IPFS: Kubo i Pinata

**Język:** Java 21; Spring RestClient.

Plan: implementacje portu `ReportStorage`: `KuboReportStorage` (lokalnie i CI) oraz
`PinataReportStorage` (demo), kontrolowana kanonizacja raportu zgodna z protokołem.
Wysyłaj dokładnie bajty JCS jako plik, nie jako obiekt JSON do ponownej serializacji.
Endpoint i format odpowiedzi Pinaty zweryfikuj w bieżącej dokumentacji (zweryfikuj:
stary `/pinning/pinJSONToIPFS` jest legacy); sprawdź limity planu przed pętlami symulatora.
Waliduj status HTTP i prawidłowy CID (CIDv0 i CIDv1); pusty CID jest błędem.

Nie dodawaj do raportu timestampów backendu ani metadanych zmieniających
podpisaną treść. CID zapisz trwale przed zleceniem blockchaina.
Timeout pinowania może oznaczać, że usługa wykonała operację: retry ma być
kontrolowany, a wielokrotne pinowanie nie może tworzyć kolejnych stage commands.

JWT Pinaty z konfiguracji, nigdy z kodu. Ustal connect/read timeout, limity danych
i jawne błędy. Testuj Kubo w Testcontainers oraz 401, rate limit, timeout, 5xx, niepoprawny JSON i brak CID.
Przy odczycie raportu weryfikuj jego schemat i hash, nie sam status HTTP.
