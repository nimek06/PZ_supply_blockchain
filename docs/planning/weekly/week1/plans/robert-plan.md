# Tydzień 1 - plan Roberta (symulator, integracja, dokumentacja)

Wersja „po ludzku” do [week1-plan.md](week1-plan.md). Techniczne tabele są tam;
tutaj opisuję, **co właściwie robisz, po co i po czym poznasz, że działa**.

## Twoja rola w jednym zdaniu

Budujesz **symulator stacji** - program, który udaje maszyny na hali: wymyśla pomiary,
podpisuje raport kluczem stacji i wysyła go do backendu. W tym tygodniu masz też
zadanie krytyczne dla całego zespołu: **wspólny wektor podpisu** (plik wzorcowy),
bo na nim wszyscy sprawdzają, że ich kod liczy to samo.

## Słowniczek

| Słowo | Co to znaczy po ludzku |
|---|---|
| Stacja | Jedno stanowisko na linii (np. montaż). W symulacji to po prostu klucz kryptograficzny + certyfikat. |
| Klucz prywatny / adres | Klucz prywatny służy do podpisywania i musi być tajny. Z niego wynika publiczny adres (np. `0x...`), którym stacja jest znana systemowi. |
| Certyfikat stacji | Dokument JSON podpisany przez administratora: „ta stacja (adres) jest uprawniona do etapów X do daty Y”. To nie jest certyfikat X.509 ani sprzętowy. |
| Wektor podpisu | Plik wzorcowy: znany raport, znany klucz testowy, oczekiwane bajty, hash i podpis. Wszyscy (Python, Java, Solidity) mają dostać z niego dokładnie te same wartości. |
| JCS | Sposób zapisu JSON-a, który daje identyczne bajty w każdym języku. Hash liczymy z tych bajtów. |
| Keccak-256 | Funkcja skrótu Ethereum (to nie jest „SHA3-256”). |
| EIP-712: `StageCompletion` | Podpis stacji pod: partia, etap, hash raportu, wynik QA, termin. **Bez nonce.** |
| EIP-712: `StageApproval` | Podpis operatora zmiany pod: partia, etap, hash raportu, termin. Używany tylko przy kontroli (etap 2). |
| Pydantic | Biblioteka Pythona do ścisłych modeli danych. Odrzuca nieznane pola i błędne typy. |
| CI | Automatyczne uruchamianie testów na GitHubie po każdej zmianie (GitHub Actions). |

## Co będzie istniało w piątek wieczorem

- Pakiet Pythona `supply_chain_simulator` z poleceniem `python -m supply_chain_simulator --help`.
- Modele raportu, kanonizacja JCS, hash i podpisy `StageCompletion` / `StageApproval` oraz testy `pytest`.
- **`shared/fixtures/signature-vector.json`** opublikowany **we wtorek wieczorem**.
- Szkic profili pomiarów (z Kubą) i szkic certyfikatu stacji (`station-certificate.schema.json` + przykład).
- Generowanie klucza stacji i wystawianie certyfikatu (szkic CLI).
- Klient, który wysyła raport do endpointu `POST /stages` Mikołaja.
- Szkielet CI na GitHubie (joby: contracts, backend, simulator).
- Pierwszy scenariusz w YAML (np. „poprawna partia 0 -> 3” i „QA fail”).

## Dzień po dniu

### Poniedziałek - szkielet pakietu

**Co robisz:**
- `simulator/pyproject.toml` z `requires-python = ">=3.11"`, układ `src/`, wirtualne środowisko (**użyj Pythona 3.12**; na tej maszynie jest 3.14, a zgodność z `eth-account` trzeba najpierw sprawdzić).
- Zależności: `eth-account`, `pydantic` v2, `httpx`, a do testów `pytest`.
- Szkielet CLI (`python -m supply_chain_simulator --help`).
- Spotkanie startowe (2 h).

**Po co:** wtorek ma być w całości na wektor, więc narzędzia mają działać już teraz.

**Poznasz, że działa:** `pytest` uruchamia się (nawet na pustym teście), a `--help` pokazuje pomoc.

### Wtorek - najważniejszy dzień zespołu: wektor podpisu

**Co robisz (w tej kolejności):**
1. Modele raportu v1 w Pydantic (`schemaVersion`, `batchId`, `stageId`, `stationAddress`, `timestamp`, `measurements`, `qaPassed`), z odrzucaniem nieznanych pól i **liczbami całkowitymi** (bez ułamków).
2. Kanonizacja JCS -> bajty -> Keccak-256 -> `reportHash`.
3. Podpis EIP-712 `StageCompletion` kluczem stacji i `StageApproval` drugim kluczem (operatora).
4. Zapis wszystkiego do `shared/fixtures/signature-vector.json`: **klucze testowe** (jawne, przeznaczone tylko do testów, nigdy prawdziwe), chainId `31337`, stały adres kontraktu, raport, bajty po kanonizacji, hash, digesty i oba podpisy, a także przykłady negatywne (zmienione pole).
5. Ogłoszenie na kanale: „wektor jest w repo”.

**Uzgodnij wcześniej z Adamem (rano):** jaki **stały adres kontraktu** wpisujesz do domeny, żeby jego test w Solidity zgadzał się z Twoim podpisem.

**Po co:** od tego pliku zależą Adam (test w Solidity, środa), Kuba (moduł Javy, środa) i Mikołaj (`POST /stages`, czwartek). Jeśli jest spóźniony, trzy osoby stoją. Dlatego ma pierwszeństwo przed czymkolwiek innym.

**Poznasz, że działa:** `pytest` potwierdza, że wektor da się odtworzyć z modeli, a wszyscy dostają plik do końca dnia.

**Jeśli nie zdążysz ze wszystkim:** opublikuj wersję minimalną (jeden raport, jeden podpis stacji), a `StageApproval` i negatywne przykłady dodaj w środę rano - i ogłoś to.

### Środa - profile pomiarów i certyfikat

**Co robisz:**
- Z Kubą: profile pomiarów dla etapów 0..3. Nie wymyślamy norm fizycznych; profil ma jasno opisywać klucze, jednostki całkowite i granice **demonstracyjne** ([production-rules](../../production-rules.md)).
- Szkic `station-certificate.schema.json` i przykład certyfikatu: wersja, `stationId`, adres, wystawca, serial, ważność od-do, dozwolone etapy ([station-identity](../../station-identity.md)). To dokument audytowy zgodny z rejestrem stacji, który wpisze Adam do kontraktu.
- Naprawy wektora, jeśli ktoś zgłosi rozbieżność.

**Po co:** bez profili backend nie ma jak ocenić raportu, a symulator - co generować. Certyfikat jest wymaganiem PDF (każda stacja ma klucz **i** certyfikat).

**Poznasz, że działa:** przykładowy certyfikat przechodzi walidację schematu, profile są spisane na jednej stronie do akceptacji.

### Czwartek - tożsamość stacji i klient

**Co robisz:**
- `identity.py`: generowanie pary kluczy stacji **bez wypisywania klucza prywatnego do logów**; konfiguracja wskazująca certyfikat.
- Szkic narzędzia, które wystawia certyfikat (podpis administratora).
- Klient, który zbuduje raport, podpisze go i wyśle `POST /stages` zgodnie z OpenAPI (Kuba) do backendu Mikołaja.

**Po co:** po tym ma być możliwe „stacja zgłasza raport” od początku do końca (do momentu odpowiedzi `202`).

**Poznasz, że działa:** symulator generuje klucz stacji, wystawia jej certyfikat i potrafi wysłać raport (nawet jeśli backend jeszcze zwraca błąd - ważne, że podpis jest poprawny).

### Piątek - CI, scenariusz i demo

**Co robisz:**
- Szkielet GitHub Actions: joby uruchamiające testy kontraktów (`forge test`), backendu (`./mvnw verify`) i symulatora (`pytest`). Przekażesz go Adamowi, który będzie go rozwijał od tygodnia 2.
- Pierwszy scenariusz YAML w `simulator/scenarios/`: ścieżka poprawna (0 -> 3) i QA fail (`qaPassed = false`).
- Demo tygodnia (60 min): kolejność jest w [week1-plan.md](week1-plan.md).

**Po co:** CI od początku zapobiega sytuacji, w której zmiana jednej osoby po cichu psuje testy innej.

**Poznasz, że działa:** po wypchnięciu zmian na GitHub widać zielone (lub czytelnie czerwone) joby.

## Od kogo czekasz i kto czeka na Ciebie

- **Czekasz na:** Adama - uzgodnienie stałego adresu kontraktu (wtorek rano); Kubę - schematy (wtorek); Mikołaja - działający endpoint (czwartek).
- **Czekają na Ciebie:** Adam, Kuba i Mikołaj - **wektor (wtorek wieczorem)**; Adam - szkielet CI (piątek).

## Czego w tym tygodniu NIE robisz

- Nie robisz Compose - to Kuba (czwartek-piątek); ty tylko z niego korzystasz.
- Nie piszesz pętli symulującej całą linię ani scenariuszy interaktywnych (tydzień 4).
- Nie zmieniasz pól podpisu bez ogłoszenia: wektor jest wspólny.
- Nie wymyślasz fizycznych norm w profilach.

## Jeśli się zablokujesz

- `eth-account` nie działa na Pythonie 3.14 -> przełącz się na 3.12 w nowym venv.
- Nie wiesz, jak ma wyglądać kanoniczny JSON -> RFC 8785; w Pythonie jest gotowa biblioteka do JCS, nie pisz własnej.
- Wektor spóźnia się o dzień -> powiedz o tym na stand-upie zaraz, żeby Adam, Kuba i Mikołaj mogli przełączyć się na inne zadania.
