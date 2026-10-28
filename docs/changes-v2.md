# Zmiany v2 (2026-10-27) - urealnienie projektu

**Status:** zmiany wynikają z analizy spójności i ryzyk, zatwierdzonej przez
użytkownika 2026-10-27. Zespół potwierdza je na spotkaniu startowym
([plan tygodnia 1](weekly_plans/week1/week1-plan.md)); ewentualny sprzeciw wymaga zmiany tego pliku
i dokumentów zależnych, nie lokalnego obejścia w kodzie.
Ten dokument jest **wiążący**, gdy starszy tekst innego pliku mówi inaczej.

## Po co zmiany

1. Sekwencyjny nonce stacji + wolne QA + brak recovery mógł trwale zablokować stację.
2. Dwa klucze przez `approveStage` w storage dawały konflikty operatorów,
   problem z cofnięciem roli, koszt gazu operatora i dodatkowy indexer.
3. Certyfikat stacji egzekwowany tylko w backendzie rozjeżdżał się z rolą on-chain.
4. Zakres (SIWE+cookie+CSRF, replacement transakcji, pełna odbudowa po reorg,
   PWA) przekraczał możliwości 4 osób w 12 tygodniach; osoba 2 była wąskim gardłem.
5. Zbyt późna integracja z Amoy (tydzień 10) i brak HTTPS dla kamery.

## Spis zmian

| ID | Było | Jest |
|---|---|---|
| V2-01 | `StageCompletion` z `nonce`; licznik nonce stacji w kontrakcie | **Brak nonce.** Replay chroni: jedno ukończenie na `(batchId, stageId)`, `deadline`, domena EIP-712. Typ: `StageCompletion(bytes32 batchId,uint32 stageId,bytes32 reportHash,bool qaPassed,uint256 deadline)` |
| V2-02 | DEC-09 otwarta (A/B/C/D) | **Zamknięta:** wariant C uproszczony. Nowy raport tego samego etapu dopiero gdy stary nie może już zostać wykonany (jego deadline minął w potwierdzonym bloku). Bez unieważniania nonce i bez zmian ABI. Maksymalne okno deadline egzekwuje kontrakt |
| V2-03 | `approveStage` on-chain, approval w storage, `qa_projections` | **Approval podpisem EIP-712 poza łańcuchem:** `StageApproval(bytes32 batchId,uint32 stageId,bytes32 reportHash,uint256 deadline)`. Operator podpisuje portfelem (bez gazu), backend zbiera podpis, kontrakt weryfikuje oba podpisy atomowo w `completeStage` |
| V2-04 | `STATION_ROLE` + certyfikat tylko w backendzie | **Rejestr stacji on-chain:** `registerStation(address,bytes32 stationId,uint64 expiresAt,uint32 stageMask)` i `revokeStation(address)` (admin). Kontrakt egzekwuje aktywność, ważność i zakres etapów. JSON certyfikatu zostaje jako poświadczenie audytowe (wymóg PDF) i musi zgadzać się z rejestrem |
| V2-05 | `qaPassed=false` -> `422`, brak śladu | **Ślad odrzucenia:** `completeStage` z `qaPassed=false` nie przesuwa partii i emituje `StageRejected`. Backend przyjmuje `202`, wynik polecenia ma `outcome=REJECTED` |
| V2-06 | SIWE + cookie sesyjna + CSRF + `__Host-` | **SIWE (EIP-4361) tylko do wydania tokenu**, potem `Authorization: Bearer`. Token losowy, w bazie tylko jego skrót SHA-256; klient trzyma go w pamięci. Brak cookie, CSRF i wymogu HTTPS dla API |
| V2-07 | Replacement/fee-bump transakcji relayera | **Bez fee-bump w MVP.** Zapis podpisanej transakcji przed wysłaniem, ponowne wysłanie identycznych bajtów, uzgadnianie hasha. Zablokowana transakcja: procedura ręczna |
| V2-08 | Pełna odbudowa projekcji po reorg | **Głębokość potwierdzeń + okno ponownego skanu** ostatnich N bloków z porównaniem block hash. Test reorg na Anvil (`evm_snapshot`/`evm_revert`) |
| V2-09 | `.send()` z wrappera Web3j | **Web3j tylko do kodowania wywołań i podpisu.** Własne podpisywanie transakcji, własny dostawca opłat EIP-1559 z konfigurowalnym minimalnym tipem. `DefaultGasProvider` zabroniony |
| V2-10 | EIP-712 w Javie przez gotowy koder | **Ręczna implementacja** jednej stałej struktury (Keccak + odzyskanie adresu), sprawdzona wspólnym wektorem w Pythonie, Javie, TS i Solidity |
| V2-11 | `batchId` losowy `bytes32` | **Numer partii** `B-RRRR-NNNN` (np. `B-2026-0001`) nadaje backend; `batchId = keccak256(utf8(batchNumber))`, liczone także on-chain w `createBatch(string batchNumber)`. QR i ręczne wpisanie używają numeru |
| V2-12 | Pinata jedyną implementacją, JSON jako obiekt | **Port `ReportStorage`:** Kubo lokalnie i w CI, Pinata do demo. Wysyłane są dokładnie bajty JCS jako plik. Walidator CID przyjmuje CIDv0 i CIDv1. Limity planu Pinaty sprawdzić przed pętlami symulatora |
| V2-13 | Amoy w tygodniu 10 | **Smoke deploy na Amoy w tygodniu 1**, integracja pionowa także na Amoy w tygodniu 5, próba demo w tygodniu 10. `evm_version` ustawiony jawnie w `foundry.toml` |
| V2-14 | Kamera/QR bez planu HTTPS | **Lokalny HTTPS dla frontendu** (certyfikat deweloperski lub tunel) od tygodnia 1; wybór biblioteki QR po spike'u |
| V2-15 | Spring Boot 3.3+ | **Aktualna wspierana wersja** wybrana przy inicjalizacji; sprawdzić zgodność z Web3j i Jackson |
| V2-16 | Podział prac z osobą 2 przeciążoną | Patrz [nowy podział zadań](team-split.md) |
| V2-17 | 14 otwartych DEC | Domknięte lub ustawione domyślnie; patrz [decyzje](decisions.md) |

## Nowa powierzchnia kontraktu (projekt; ABI wygeneruje kompilator)

```text
createBatch(string batchNumber) -> bytes32 batchId        // RELAYER_ROLE
completeStage(
    bytes32 batchId, uint32 stageId, bytes32 reportHash,
    bool qaPassed, uint256 deadline,
    string ipfsCid, bytes stationSignature,
    uint256 approvalDeadline, bytes approvalSignature)    // RELAYER_ROLE
registerStation(address station, bytes32 stationId,
                uint64 expiresAt, uint32 stageMask)       // DEFAULT_ADMIN_ROLE
revokeStation(address station)                            // DEFAULT_ADMIN_ROLE
setMaxDeadlineWindow(uint256 seconds_)                    // DEFAULT_ADMIN_ROLE
getBatch(bytes32 batchId) -> (exists, nextStage, completed)
getStation(address station) -> (active, stationId, expiresAt, stageMask)

BatchCreated(bytes32 indexed batchId, string batchNumber)
StageCompleted(bytes32 indexed batchId, uint32 stageId, address indexed station,
               address qaOperator, bytes32 reportHash, string ipfsCid)
StageRejected(bytes32 indexed batchId, uint32 stageId, address indexed station,
              bytes32 reportHash, string ipfsCid)
StationRegistered(address indexed station, bytes32 stationId, uint64 expiresAt, uint32 stageMask)
StationRevoked(address indexed station)
```

Role: `DEFAULT_ADMIN_ROLE`, `RELAYER_ROLE`, `QA_INSPECTOR_ROLE`, `OPERATOR_ROLE`
(sprawdzana przez API; kontrakt jej nie używa). **`STATION_ROLE` usunięta** -
zastępuje ją rejestr stacji.

Reguły `completeStage` w kolejności:

1. Nadawca ma `RELAYER_ROLE`; partia istnieje i nie jest zakończona.
2. `stageId == nextStage`; `block.timestamp <= deadline` oraz
   `deadline <= block.timestamp + maxDeadlineWindow`.
3. Odzyskany z `stationSignature` adres jest aktywną, niewygasłą stacją,
   której `stageMask` zawiera `stageId`. Podpis dotyczy typu `StageCompletion`.
4. Jeśli `qaPassed == false`: emituj `StageRejected`, zapisz skrót autoryzacji,
   by nie powielać zdarzenia, i zakończ **bez zmiany stanu partii**.
5. Dla etapu kontroli (`stageId == 2`): z `approvalSignature` odzyskaj operatora
   typu `StageApproval`; wymagane `block.timestamp <= approvalDeadline`,
   aktualna `QA_INSPECTOR_ROLE` operatora i **adres różny od adresu stacji**.
6. Zapisz ukończenie (`nextStage++`, ewentualnie `completed`), emituj `StageCompleted`
   z `qaOperator` (`address(0)` dla etapów bez bramki).

Backend sprawdza te same reguły wstępnie, ale kontrakt im nie ufa.

## Reguła zastępowania raportu (V2-02)

Dla `(deployment, batchId, stageId)`:

| Stan istniejącego polecenia | Nowe zgłoszenie z innym `reportHash` |
|---|---|
| identyczny digest | zwróć istniejące `commandId` |
| aktywne (ACCEPTED..SUBMITTED, WAITING_FOR_QA) | `409 STAGE_ALREADY_PENDING` |
| CONFIRMED z `outcome=ACCEPTED` | `409` (etap ukończony) |
| CONFIRMED z `outcome=REJECTED` | dozwolone (odrzucenie nie kończy etapu) |
| FAILED | dozwolone dopiero gdy potwierdzony blok ma `timestamp` większy od starego `deadline` i brak nierozstrzygniętych prób |

Stary podpis nie może się wtedy już wykonać, bo kontrakt odrzuci go po deadline.
Okno oczekiwania jest ograniczone przez `maxDeadlineWindow` (robocza wartość
3600 s, DEC-11). Podpis z `qaPassed=false` jest bezpieczny do natychmiastowego
zastąpienia: nie przesuwa partii.

## Co zostaje bez zmian

Wymagania PDF (dwa niezależne klucze przy kontroli, certyfikat stacji, hybrydowy
zapis IPFS/on-chain, RBAC, maszyna stanów, QR, paszport), marszruta 0..3,
JCS + Keccak-256, jeden relayer, PostgreSQL jako trwała kolejka, lokalnie Anvil,
docelowo Amoy (`chainId = 80002`).
