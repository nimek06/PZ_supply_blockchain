# Modele transportowe API

**Język:** Java 21, preferowane immutable `record`.

Plan: `StageSubmissionRequest`, `TelemetryReportRequest`, `CreateBatchRequest`,
`CommandAcceptedResponse`, `CommandStatusResponse`, `BatchResponse`,
`BatchHistoryResponse` i model błędu zgodny z Problem Details.

Nazwy/właściwości JSON muszą odpowiadać OpenAPI i JSON Schema.
Duży nonce przyjmuj jako dziesiętny string z jawną walidacją zakresu uint256;
nie używaj Java `long` dla całego uint256. Dla stageId pilnuj zakresu uint32.
Sprawdź zagnieżdżone pola przez `@Valid`. Zabroń nieznanych pól i niejawnych
konwersji w konfiguracji Jackson.

DTO nie ma adnotacji JPA, wywołań HTTP, signera ani reguł przejścia stanu.
Mapuj do domeny w jasnym miejscu. Odpowiedź publiczna nie ujawnia
wewnętrznych danych kolejki ani pełnych wyjątków.

StageSubmissionRequest zawiera też `stationCertificateId` poza raportem JCS.
Nie traktuj niezweryfikowanego ID jako dowodu tożsamości.
DTO kolejki QA opisują raport oczekujący na podpis operatora zmiany,
nie ukończony etap z historii publicznej.
