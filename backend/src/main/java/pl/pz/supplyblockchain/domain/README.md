# Domena

**Język:** czysta Java 21.

Plan: `BatchId`, `BatchState`, `StageId`, `TelemetryReport`, `StageCommand`,
`CommandStatus` oraz konkretne błędy reguł.
Użyj value objects tam, gdzie rzeczywiście chronią format/invariant,
nie opakowania dla każdego stringa bez zachowania.

Tutaj: zgodność identyfikatorów, dozwolone jednostki, statusy polecenia
i reguły wstępne etapów. Kontrakt jest ostatecznym autorytetem on-chain;
nie twórz innej maszyny stanów niż wspólnie zatwierdzona.

Brak zależności od Spring, Jackson, JPA, Web3j i HTTP.
Oddziel raport stacji od współzatwierdzenia przez operatora zmiany z rolą QA
i od rekordu transakcji relayera. Kontrola wymaga dwóch niezależnych kluczy.
Model certyfikowanej tożsamości odróżnia stationId, adres, wystawcę,
ważność i odwołanie; sam certyfikat nie nadaje STATION_ROLE.
Testy domeny uruchamiaj bez kontekstu Spring i bez sieci.
