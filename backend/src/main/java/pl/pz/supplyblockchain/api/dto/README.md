# Modele transportowe API

**Język:** Java 21, preferowane immutable `record`.

Plan: `StageSubmissionRequest`, `TelemetryReportRequest`, `CreateBatchRequest`,
`CommandAcceptedResponse`, `CommandStatusResponse`, `BatchResponse`,
`BatchHistoryResponse` i model błędu zgodny z Problem Details.

Nazwy/właściwości JSON muszą odpowiadać OpenAPI i JSON Schema.
`StageCompletion` nie ma nonce. `deadline` i inne uint256 przyjmuj jako dziesiętny
string z walidacją zakresu; nie używaj Java `long` dla całego uint256. Dla stageId pilnuj uint32.
`batchNumber` ma format `B-RRRR-NNNN`; `batchId` = keccak256(utf8(batchNumber)).
Sprawdź zagnieżdżone pola przez `@Valid`. Zabroń nieznanych pól i niejawnych
konwersji w konfiguracji Jackson.

DTO nie ma adnotacji JPA, wywołań HTTP, signera ani reguł przejścia stanu.
Mapuj do domeny w jasnym miejscu. Odpowiedź publiczna nie ujawnia
wewnętrznych danych kolejki ani pełnych wyjątków.

StageSubmissionRequest zawiera też `stationCertificateId` poza raportem JCS.
Nie traktuj niezweryfikowanego ID jako dowodu tożsamości.
DTO kolejki QA opisują raport oczekujący na podpis operatora zmiany,
nie ukończony etap z historii publicznej; zgłoszenie approval niesie podpis
`StageApproval` i `approvalDeadline`. Status polecenia ma `outcome` (ACCEPTED/REJECTED).
