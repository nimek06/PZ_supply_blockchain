# Wspólne dane partii

**Język:** TypeScript/TSX; TanStack Query.

Plan: `batchQueries.ts`, `useBatch.ts`, `useBatchHistory.ts`,
`useCommandStatus.ts`, `BatchSummary.tsx`.
Typy transportowe pochodzą z API; mapowanie do UI ma być wspólne dla ekranów.

Klucze query zawierają batchId oraz środowisko/sieć tam, gdzie mogą zmienić dane.
Polling ogranicz i zatrzymuj po terminalnym statusie; po potwierdzeniu
invaliduj partię i historię. Error nie staje się pustą listą.
Cache nie oznacza finalności: pokaż czas aktualizacji i stan potwierdzenia.

Nie dubluj hooków w operator i passport. Testuj polling, zmianę batchId,
anulowanie starego zapytania i brak ponowień trwałego błędu.
