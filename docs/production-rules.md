# 1. Przebieg produkcji i reguły QA

## Status i zakres

**Ustalone z użytkownikiem 2026-10-27:** ogólne etapy
przygotowanie -> montaż -> kontrola -> pakowanie, bez konkretnego produktu.
Nie przyjmujemy testu ciśnieniowego ani norm dla wybranego wyrobu.
Numeracja poniżej porządkuje ten wariant MVP; parametry pomiarów wymagają
osobnego profilu demonstracyjnego przed generowaniem schematów i testów.

Wymagania nadrzędne: [opis PDF](source-alignment.md), REQ-002..005 i REQ-014
z [specyfikacji](spec.md). To projekt logiki, nie gotowa implementacja.

## Stany partii

`nextStage` jest numerem **oczekiwanego**, a nie ostatnio ukończonego etapu.

| Stan logiczny | Dane kontraktu | Co wolno zrobić |
|---|---|---|
| Nie istnieje | `exists=false` | uprawnione createBatch, nigdy completeStage |
| Czeka na przygotowanie | `exists=true`, `nextStage=0`, `completed=false` | etap 0 |
| Czeka na montaż | `nextStage=1`, `completed=false` | etap 1 |
| Czeka na kontrolę | `nextStage=2`, `completed=false` | etap 2, z dwoma kluczami |
| Czeka na pakowanie | `nextStage=3`, `completed=false` | etap 3, dopiero po zamknięciu kontroli |
| Zakończona | `completed=true`, brak następnego etapu | odczyt historii, bez nowych completeStage |

Propozycja techniczna: po ostatnim etapie pozostaw `nextStage=3` i ustaw
`completed=true`; w API zwracaj `nextStage=null` dla zakończonej partii.
Nie stosuj wartości `4` jako piątego etapu. DTO ma być jednoznaczne,
nie kopiować bez interpretacji pól storage.

## Tabela marszruty

| stageId / kod | Warunek wejścia | Co poświadcza stacja | QA przed zamknięciem | Wynik poprawnego przejścia |
|---|---|---|---|---|
| 0 / PREPARATION | potwierdzone utworzenie partii | zakończenie listy sprawdzeń przygotowania | pozytywny wynik stacji | nextStage=1 |
| 1 / ASSEMBLY | kanoniczny StageCompleted dla 0 | wykonanie zadanej operacji montażowej | pozytywny wynik stacji | nextStage=2 |
| 2 / QUALITY_CONTROL | kanoniczny StageCompleted dla 1 | przeprowadzenie listy kontroli i jej wynik | pozytywny wynik stacji **oraz** podpis `StageApproval` operatora zmiany dla tego raportu | nextStage=3 |
| 3 / PACKAGING | kanoniczny StageCompleted dla 2, z wymaganą współautoryzacją | zakończenie przygotowania partii do przekazania | pozytywny wynik stacji | completed=true |

Nie ma przejścia „operator ustawia dowolny status”. Przyciski uruchamiają
autoryzowane operacje, a projekcję zmienia indexer po potwierdzonym zdarzeniu.
Każdy etap ma raport stacji; etap 2 dodatkowo wymaga niezależnego klucza
operatora. Kolejne etapy nie mogą powstawać na podstawie samego HTTP 202.

## Reguły obowiązujące każdy etap

1. Rozpoznana sieć i kontrakt, istniejąca niezakończona partia.
2. `stageId` równe oczekiwanemu etapowi, bez przeskakiwania i cofania.
3. Raport zgodny z wersją schematu i profilem pomiarów dla tego etapu.
4. `batchId` ścieżki, raportu i danych podpisanych są identyczne.
5. JCS/Keccak raportu odpowiada podpisanemu `reportHash`.
6. Odzyskany signer równa się `stationAddress`; aktywna, niewygasła rejestracja stacji
   on-chain, której `stageMask` obejmuje etap, oraz zgodny certyfikat w backendzie.
7. Nowa operacja ma niewygasły `deadline` w oknie `maxDeadlineWindow`; brak nonce
   (replay chroni jedno ukończenie na `(batchId, stageId)`).
8. `qaPassed` zgodne z wynikiem walidacji pomiarów w backendzie. `qaPassed=false`
   kończy się `StageRejected` bez przesunięcia partii; `true` jest warunkiem przejścia.
9. Raport jest przypięty, CID zapisany; na etapie 2 istnieje podpis `StageApproval`
   niezależnego operatora według [protokołu dwóch kluczy](quality-approval.md).
10. Kontrakt ponawia własne sprawdzenia i aktualizuje stan atomowo.
    Indexer dopiero po wymaganych potwierdzeniach aktualizuje widok API.

Lookup identycznego, już przyjętego zamiaru jest wyjątkiem od sprawdzania
bieżącego deadline/stanu etapu: odtwarza istniejący wynik, nie wykonuje nowego etapu.
Zasady dostępu pozostają obowiązujące; szczegóły w [access-control](access-control.md).

## Pomiary: co trzeba określić, a czego nie wymyślamy

Bez konkretnego produktu nie istnieje uzasadniona temperatura, moment
dokręcania lub ciśnienie „poprawne dla całej linii”. Nie wpisujemy takich
wartości do kontraktu ani nie nazywamy przypadkowych progów normą.

Przed implementacją osoba 2 i 4 przygotowują **jeden wersjonowany profil
demonstracyjny** dla każdego etapu:

| Parametr profilu | Wymagana treść |
|---|---|
| Identyfikacja | wersja protokołu, stageId i niezmienny identyfikator profilu |
| Pomiar | klucz JSON, znaczenie, jednostka, typ i wymagalność |
| Reprezentacja | całkowite jednostki; bez floatów, NaN, Infinity i konwersji string -> number |
| Ocena | dolna/górna granica z określeniem inkluzywności albo inna jednoznaczna reguła |
| Kompletność | wymagane klucze, zakaz nieznanych pól; brak pomiaru to nie PASS |
| Czas | maksymalny wiek raportu i dopuszczalne odchylenie zegara |
| Testy | poprawny przykład, obie granice, wartości poza nimi i brak pola |

Dopuszczalny jest profil ogólnych sprawdzeń zamiast parametrów fizycznych,
ale jego pola i warunki też muszą być zatwierdzone. `qaPassed` jest podpisanym
wynikiem stacji, nie niezależnym dowodem wykonania pomiaru.
Zmiana profilu nie może reinterpretować dawnych raportów. W v1 najprościej
zamrozić jeden zestaw profili na wdrożenie; zmiana formatu wymaga nowej wersji.
Jeżeli kilka profili ma działać jednocześnie, identyfikator profilu musi trafić
do raportu objętego hashem i schematu przed wdrożeniem, nie tylko do ustawień UI.

## Granice weryfikacji

| Warstwa | Co sprawdza | Czego nie dowodzi |
|---|---|---|
| Symulator | model, jednostki, własne wyliczenie qaPassed, podpis | rzeczywistych zjawisk na fizycznej hali |
| Backend | schemat, profil, zgodność qaPassed, certyfikat, podpis, stan wstępny | niezawodności czujnika i finalnego wykonania transakcji |
| Kontrakt | RBAC, rejestr stacji, podpis/deadline, kolejność, qaPassed i approval | temperatury lub innych danych ukrytych pod reportHash |
| Operator | raport, kontekst partii, zgodność hash i decyzję kontroli (podpis `StageApproval`) | automatycznej prawdziwości wszystkich pomiarów |
| Paszport | zgodność JSON z potwierdzonym hash | spełnienia norm przemysłowych lub certyfikacji produktu |

Zakres etapów stacji egzekwuje kontrakt przez `stageMask` w rejestrze stacji
(V2-04), więc relayer nie może go obejść.

## Powtórzenie i korekta

- QA fail (`StageRejected`) lub brak approval nie przesuwa partii.
- Nie wracamy z pakowania do montażu i nie nadpisujemy ukończonego etapu.
- Powtórny pomiar przed ukończeniem jest nowym raportem; obowiązuje
  [reguła zastępowania](corrections.md) (po `StageRejected` od razu, po FAILED
  dopiero po deadline starego podpisu).
- Fizyczny rework, cofnięcie etapów i anulowanie partii nie należą obecnie
  do zdefiniowanej marszruty. Nie wolno ich implementować jako edycji historii.
- Alternatywy i blokady opisuje [corrections](corrections.md).

## Odbiór reguł

Testy mają wykazać dokładnie `0 -> 1 -> 2 -> 3 -> completed`, brak
przejścia poza kolejnością i po zakończeniu, brak zmiany stanu przy rewercie,
blokadę negatywnego QA (`StageRejected`) oraz wymaganie obu niezależnych kluczy na etapie 2.
Wersja schematu i profile pomiarów pozostają bramką przed implementacją
walidacji, nie powodem do oznaczenia qaPassed=true dla brakujących danych.
