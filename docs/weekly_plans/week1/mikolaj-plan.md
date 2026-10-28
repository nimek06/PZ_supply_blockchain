# Tydzień 1 - plan Mikołaja (backend)

Wersja „po ludzku” do [week1-plan.md](week1-plan.md). Techniczne tabele są tam;
tutaj opisuję, **co właściwie robisz, po co i po czym poznasz, że działa**.

## Twoja rola w jednym zdaniu

Budujesz **backend** - „okienko przyjęć” systemu: stacja przysyła podpisany raport,
Ty sprawdzasz go, **bezpiecznie zapisujesz** i odpowiadasz „przyjęte” (kod `202`).
Dopiero później inne części (tydzień 4-5) wyślą go do IPFS i na blockchain.
W tym tygodniu pracujesz tylko nad początkiem tej drogi.

## Słowniczek

| Słowo | Co to znaczy po ludzku |
|---|---|
| `202 Accepted` | „Przyjąłem Twoje zgłoszenie i mam je zapisane, ale jeszcze go nie wykonałem”. To **nie** znaczy, że etap jest ukończony. Kod `202` wolno zwrócić dopiero po udanym zapisie do bazy. |
| Polecenie (command) | Zapis w bazie „ktoś chce zrobić X”. Ma status i numer (`commandId`). |
| Idempotencja | Jeśli stacja wyśle to samo zgłoszenie 10 razy (bo np. zerwało się połączenie), efekt ma być jeden, a odpowiedź za każdym razem ta sama. |
| Digest | Skrót podpisywanej wiadomości EIP-712. Po nim rozpoznajesz „to samo zgłoszenie”. |
| Reguła zastępowania | Zasada: jeśli etap ma już aktywne zgłoszenie, nowe z inną treścią dostaje `409`. Nowe po błędzie wolno dopiero, gdy stary podpis na pewno wygasł. Szczegóły: [corrections](../../corrections.md). |
| Flyway / migracja | Pliki SQL, które tworzą i zmieniają tabele w bazie w uporządkowany sposób. |
| Testcontainers | Biblioteka, która na czas testu uruchamia prawdziwego PostgreSQL w Dockerze. Nie używamy H2, bo zachowuje się inaczej niż produkcyjna baza. |
| Web3j | Biblioteka Javy do rozmowy z blockchainem. U nas służy tylko do zakodowania wywołania i podpisania transakcji. |
| EIP-1559 | Nowoczesny sposób ustalania opłat za transakcję (opłata bazowa + tip). Domyślne ustawienia Web3j są stare i na Polygonie by się „zacięły”. |
| SIWE | „Sign-In With Ethereum”: logujesz się, podpisując portfelem krótką wiadomość. Nie ma hasła. Backend po weryfikacji wydaje **token**. |
| Token Bearer | Losowy długi ciąg, który klient wysyła w nagłówku `Authorization`. W bazie trzymasz tylko jego skrót (SHA-256), więc wyciek bazy nie ujawnia tokenów. |

## Co będzie istniało w piątek wieczorem

- Działająca aplikacja Spring Boot (`./mvnw verify` przechodzi, `/actuator/health` odpowiada).
- Pierwsze migracje bazy i tabela `commands` (oraz szkice `qa_approvals`, `auth_*`).
- Endpoint `POST /api/v1/batches/{batchId}/stages`, który:
  - sprawdza format raportu, zgodność hasha i podpisu;
  - zapisuje polecenie i dopiero po zapisie zwraca `202` z `commandId`;
  - przy powtórzeniu tego samego zgłoszenia zwraca ten sam `commandId`;
  - przy złym podpisie zwraca `403`, przy zmienionym pomiarze `400`/`403`.
- Notatka i działający przykład: jak kodować wywołanie `completeStage`, podpisywać transakcję i ustawiać opłaty EIP-1559 na Anvil.
- Początek logowania: `POST /auth/challenges` i `POST /auth/sessions` wydające token.

## Dzień po dniu

### Poniedziałek - start aplikacji

**Co robisz:**
- Generujesz projekt Spring Initializr: Java 21, Maven, **aktualna wspierana wersja** Spring Boot (nie „3.3+”), pakiet `pl.pz.supplyblockchain`. Dodajesz: Web, Validation, Data JPA, PostgreSQL, Flyway, Actuator.
- Dodajesz Web3j osobno i **sprawdzasz, czy dogaduje się z wersją Jacksona** użytą przez Spring Boot.
- Przenosisz `pom.xml`, Maven Wrapper i klasę startową do istniejącej struktury katalogów (nie nadpisuj README w folderach).
- Idziesz na spotkanie startowe.

**Po co:** konflikty wersji bibliotek lepiej znaleźć na początku. Jeśli Web3j nie zgrywa się z nową wersją Spring Boota, wybierasz starszą wspieraną wersję i zapisujesz decyzję.

**Poznasz, że działa:** aplikacja startuje, `/actuator/health` zwraca `UP`.

### Wtorek - czytasz plan, którego Ty nie piszesz

**Co robisz:**
- Recenzujesz szkic `openapi.yaml` od Kuby: czy endpointy, statusy i błędy mają sens z punktu widzenia backendu? Czy odpowiedzi `202`, `409`, `422` są tam, gdzie trzeba?
- Szkicujesz migracje według [data-model](../../data-model.md): tabela `commands` (z digestem, statusem, `outcome`, `retry_allowed_after`), `qa_approvals`, `auth_challenges`, `auth_sessions`.

**Po co:** OpenAPI to umowa między backendem a frontendem. Jeśli ją zaakceptujesz bez czytania, w tygodniu 5 wyjdą niespójności. Twoje uwagi są potrzebne właśnie teraz.

**Poznasz, że działa:** Kuba dostaje listę uwag, a Ty masz szkic SQL (jeszcze nie musi być uruchomiony).

### Środa - jak backend będzie wysyłał transakcje (spike Web3j)

**Co robisz:** na lokalnym Anvil (uruchom przez Docker lub poproś Adama o instrukcję, a w czwartek będzie gotowe Compose od Kuby) piszesz mały program, który:
1. koduje wywołanie funkcji kontraktu;
2. **sam podpisuje** transakcję kluczem relayera;
3. ustawia opłaty w stylu EIP-1559 z konfigurowalnym minimalnym tipem;
4. wysyła i odczytuje potwierdzenie.

**Czego nie robisz:** nie używasz `contract.metoda().send()` ani `DefaultGasProvider`. Powód: potrzebujemy **zapisać podpisaną transakcję w bazie przed wysłaniem**, żeby po restarcie wysłać dokładnie te same bajty i nie zrobić podwójnego przejścia. Metoda `.send()` ukrywa podpisanie i wysyła od razu.

**Po co:** relayer to najbardziej ryzykowna część backendu. Lepiej poznać jego pułapki w tygodniu 1 niż w tygodniu 4. Do spike'u nie potrzebujesz jeszcze ABI od Adama - wystarczy prosty testowy kontrakt lub zwykły transfer.

**Poznasz, że działa:** transakcja podpisana „ręcznie” przechodzi na Anvil, a w notatce masz: której biblioteki użyłeś, jak liczysz opłaty i jak zachowa się tip na Polygonie.

**W środę Kuba pisze obok Ciebie moduł krypto** (hash i podpis w Javie) - jesteście w parze; zapytaj, jak zaczyna, żebyście od czwartku mówili tym samym językiem.

### Czwartek - pierwsze prawdziwe zgłoszenie

**Co robisz:**
- Pierwsza migracja Flyway i encje dla `commands`.
- Endpoint `POST /api/v1/batches/{batchId}/stages` używający **modułu krypto od Kuby** (przyjdzie w czwartek rano): sprawdza hash raportu i odzyskuje adres z podpisu.
- Zapis polecenia z digestem, idempotencja, reguła zastępowania, `202` **dopiero po commit** transakcji bazodanowej.
- Test integracyjny na PostgreSQL (Testcontainers lub Compose Kuby z czwartkowego popołudnia).

**Po co:** to najważniejszy test całego tygodnia po stronie backendu - dowodzi, że to, co podpisał Robert w Pythonie, backend rozumie tak samo i potrafi bezpiecznie zapamiętać.

**Poznasz, że działa:** test wysyła poprawne zgłoszenie (`202`), to samo drugi raz (ten sam `commandId`), zgłoszenie ze zmienionym pomiarem (odrzucone), ze złym podpisem (`403`) i inne zgłoszenie dla tego samego etapu (`409`).

**Jeśli moduł Kuby jeszcze nie jest gotowy:** zacznij od migracji i szkieletu kontrolera z atrapą weryfikacji, a podmień ją po przekazaniu modułu. Nie pisz własnej wersji krypto równolegle.

### Piątek - logowanie portfelem (początek)

**Co robisz:** `POST /api/v1/auth/challenges` (losowa jednorazowa wiadomość SIWE związana z adresem i domeną) oraz `POST /api/v1/auth/sessions` (sprawdzenie podpisu, jednorazowe zużycie challenge, wydanie tokenu; w bazie tylko jego skrót SHA-256). Bez sprawdzania ról - to tydzień 2-3.

**Po co:** prywatne odczyty (status polecenia, lista kandydatów QA) mają wymagać zalogowania. Token zamiast cookie oznacza brak CSRF i działa tak samo dla przeglądarki (przyszły frontend) i skryptu Pythona.

**Poznasz, że działa:** testy: ten sam challenge nie zadziała drugi raz, wygasły jest odrzucony, zły podpis jest odrzucony.

Potem demo z zespołem.

## Od kogo czekasz i kto czeka na Ciebie

- **Czekasz na:** Kubę - szkic OpenAPI (poniedziałek) i moduł krypto (czwartek rano), Roberta - wektor (wtorek wieczorem), Kubę - compose (czwartek po południu).
- **Czekają na Ciebie:** Kuba - recenzja OpenAPI (wtorek); Robert - działający endpoint `POST /stages`, żeby jego klient mógł się do czegoś podłączyć.

## Czego w tym tygodniu NIE robisz

- Nie piszesz workera, indexera ani zapisu do IPFS (tydzień 4-5; Kuba robi tylko badanie).
- Nie implementujesz ról ani certyfikatów (tydzień 2-3; weryfikator certyfikatu pisze Adam).
- Nie dodajesz fee-bump (podnoszenia opłaty zablokowanej transakcji) - poza MVP.
- Nie piszesz własnej implementacji JCS/EIP-712 - korzystasz z modułu Kuby.

## Jeśli się zablokujesz

- Web3j kłóci się z wersją Spring Boota -> wybierz wspierane połączenie wersji i zapisz je w `README` backendu.
- Wynik hasha w Javie różni się od Pythona -> nie zmieniaj protokołu; z Kubą i Robertem znajdźcie, w którym kroku (JCS, Keccak, kolejność pól) jest różnica.
- Nie jesteś pewien statusu HTTP -> sprawdź tabelę w [protokole](../../protocol.md).
