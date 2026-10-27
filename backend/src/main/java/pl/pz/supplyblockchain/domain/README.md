# Domena

**Język:** czysta Java 21.

Plan: `BatchNumber`/`BatchId`, `BatchState`, `StageId`, `TelemetryReport`, `StageCommand`,
`CommandStatus` oraz konkretne błędy reguł.
Użyj value objects tam, gdzie rzeczywiście chronią format/invariant,
nie opakowania dla każdego stringa bez zachowania.

Tutaj: zgodność identyfikatorów, dozwolone jednostki, statusy polecenia
i reguły wstępne etapów. Kontrakt jest ostatecznym autorytetem on-chain;
nie twórz innej maszyny stanów niż wspólnie zatwierdzona.

Brak zależności od Spring, Jackson, JPA, Web3j i HTTP.
Oddziel raport stacji od approval (podpis `StageApproval` operatora z rolą QA)
i od rekordu transakcji relayera. Kontrola wymaga dwóch niezależnych kluczy.
Model certyfikowanej tożsamości odróżnia stationId, adres, wystawcę, ważność,
odwołanie i `stageMask`; certyfikat JSON jest poświadczeniem audytowym, a uprawnienia
stacji egzekwuje rejestr on-chain (`STATION_ROLE` nie istnieje).
`batchId = keccak256(utf8(batchNumber))`, numer `B-RRRR-NNNN`.
Testy domeny uruchamiaj bez kontekstu Spring i bez sieci.
