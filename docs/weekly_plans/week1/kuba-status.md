# Tydzień 1 - co zrobił Kuba (stan na 2026-10-30)

Krótko i po ludzku. Plan zadań jest w [kuba-plan.md](kuba-plan.md), tu jest tylko to, co faktycznie powstało
i co czeka na innych.

## Zrobione

**Umowa API - `shared/schemas/openapi.yaml`**
Opis wszystkich endpointów backendu w jednym pliku: logowanie portfelem, tworzenie partii, historia partii,
wysyłanie raportu ze stacji, status polecenia i kolejka kontroli jakości z podpisem operatora. Zawiera też
format błędów i listę kodów błędów. Dzięki temu Mikołaj ma z czego pisać kontrolery, Robert klienta
w symulatorze, a Kuba w tygodniu 2 mocki do frontendu. To **szkic do przeglądu**, jeszcze nie zatwierdzony.

**Format raportu - `shared/schemas/telemetry.schema.json`**
Jak ma wyglądać raport stacji. Odrzuca nieznane pola, ułamki, czas w milisekundach i adresy wielkimi literami.
Dla każdego z czterech etapów jest szkic tego, co raport mierzy (np. ile pozycji listy sprawdzeń zaliczono).
To są **wartości demonstracyjne, nie normy fizyczne** i zespół musi je zaakceptować w piątek (DEC-02).

**Typy podpisów - `shared/schemas/eip712.json`**
Dokładne definicje dwóch podpisów (stacji i operatora) w kolejności pól wymaganej przez protokół.
Sprawdzone automatycznie względem kontraktu Adama: napisy typów są identyczne.

**Środowisko lokalne - `infrastructure/local/`**
`compose.yaml` stawia jedną komendą bazę PostgreSQL, lokalny łańcuch Anvil i lokalny IPFS (Kubo).
Wszystko słucha tylko na `127.0.0.1`, obrazy mają przypięte wersje, a każda usługa ma sprawdzenie zdrowia.
`.env.example` ma puste hasło do bazy - bez ustawienia go własnego start się celowo nie uda.
W README opisano, jak świadomie wyczyścić środowisko. Najważniejsze: po restarcie Anvila jego stan znika,
a baza zostaje, więc trzeba je wyczyścić razem.

**Narzędzia na komputerze Kuby**
Zainstalowano w katalogu domowym (bez roota): Java 21 (Temurin) i Maven 3.9.9, po sprawdzeniu sum kontrolnych.
Nie zmieniano `~/.bashrc` ani ustawień systemu. Żeby z nich korzystać, w terminalu:
`export JAVA_HOME=$HOME/.local/opt/jdk21 PATH=$HOME/.local/opt/jdk21/bin:$HOME/.local/opt/maven/bin:$PATH`

## Jak to sprawdzono

- Schemat raportu przetestowano na 15 przykładach (4 poprawne, 11 błędnych) - wszystkie zachowały się zgodnie z oczekiwaniem.
- OpenAPI przechodzi walidator specyfikacji OpenAPI 3.1.
- Składnię `compose.yaml` sprawdzono, ale **Compose nie był uruchomiony** (patrz niżej).

## Czeka na innych albo na dostęp - celowo nie ruszone

| Co | Na kogo / co czeka |
|---|---|
| Moduł krypto w Javie (kanonizacja, hash, odzysk adresu z podpisu) | Wektor testowy `shared/fixtures/signature-vector.json` od **Roberta** oraz szkielet projektu Maven od **Mikołaja**. Bez wektora nie ma jak sprawdzić, czy wynik jest dobry. |
| Pierwsze uruchomienie `docker compose up` | **Docker** nie jest zainstalowany, a instalacja wymaga hasła administratora. Trzeba go zainstalować ręcznie, a potem sprawdzić zdrowie trzech usług. |
| Badanie Pinaty | Konto i token Pinata ma **Mikołaj**. Tokenu nie należy wpisywać do repozytorium. |
| Badanie Kubo (wersja CID, pobranie przez bramkę) | Wymaga działającego Dockera. |
| Zatwierdzenie profili pomiarów | Spotkanie zespołu w piątek (DEC-02), razem z Robertem. |

## Do potwierdzenia przez zespół

- Dwa endpointy dodane jako **propozycja** (oznaczone w pliku): rejestracja certyfikatu stacji
  i odczyt bieżącej sesji. Protokół wymaga zarejestrowanego certyfikatu, ale nie mówi, jak go zarejestrować.
- Lista kodów błędów to propozycja do przeglądu z Mikołajem.
- Wybrano OpenAPI 3.1. Jeśli generator klienta we frontendzie go nie obsługuje, trzeba zmienić na 3.0.
