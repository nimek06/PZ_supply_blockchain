# 5. Uprawnienia i logowanie

**Status:** szczegółowa propozycja DEC-05/06 do zatwierdzenia, nie działające
zabezpieczenia. Wymagania RBAC wynikają z PDF; wybór sposobu sesji nie został
narzucony przez PDF ani zatwierdzony samą prośbą o rozpisanie projektu.

## Tożsamość, uprawnienie i dowód operacji

- **Tożsamość:** adres, którego klucz podpisał login challenge lub raport.
- **Sesja:** czasowy dostęp tego adresu do backendu.
- **Rola:** aktualne prawo do określonej operacji w danym wdrożeniu.
- **Podpis raportu:** dowód autoryzacji konkretnego zdarzenia; nie token sesji.
- **Certyfikat:** poświadczenie stacji; nie rola operatora i nie hasło.

Połączenie portfela, sam adres w JSON, UUID polecenia i schowany przycisk
nie dają uprawnień. Admin nie ma automatycznie wszystkich ról biznesowych.
Wersja MVP dotyczy kont EOA. Portfele kontraktowe wymagają jawnego projektu
EIP-1271 zarówno dla loginu, jak i pozostałych podpisów; nie deklarujemy ich
obsługi samym użyciem RainbowKit.

## Role i źródło ich uprawnień

| Aktor / nazwa techniczna | Źródło prawa | Zakres |
|---|---|---|
| Publiczny odbiorca | brak roli | wyłącznie publiczny paszport i potwierdzona historia |
| Operator / OPERATOR_ROLE | AccessControl w kontrakcie, sprawdzane przez API | tworzenie partii w API i własne polecenia |
| Operator zmiany QA / QA_INSPECTOR_ROLE | AccessControl w kontrakcie | prywatni kandydaci kontroli i approveStage |
| Stacja / STATION_ROLE | rola on-chain **i** aktywny certyfikat w backendzie | własne podpisane raporty i własne statusy |
| Relayer / RELAYER_ROLE | AccessControl w kontrakcie | createBatch i completeStage, nie decyzja operatora |
| Administrator / DEFAULT_ADMIN_ROLE | AccessControl w kontrakcie | nadawanie/odbieranie ról i kontrolowane czynności administracyjne |
| Wystawca certyfikatu | zaufana lista publicznych kluczy w konfiguracji backendu | wystawianie poświadczeń stacji, bez automatycznych praw relayera/QA |

Operator tworzy partię przez API, relayer wykonuje transakcję.
Kontrakt w tym wariancie sprawdza RELAYER_ROLE, a nie sesję operatora.
Audyt kto zlecił utworzenie jest off-chain. Jeśli ma być weryfikowalny
niezależnie od relayera, konieczny jest osobny podpis operatora w createBatch.

## Macierz operacji

`Własne` oznacza zgodność adresu z właścicielem zapisanym przez backend,
nie pole `owner` przyjęte od klienta. `Admin` oznacza aktualną rolę admina.

| Operacja | Odbiorca | Operator | QA | Stacja | Relayer | Admin | Gdzie egzekwować |
|---|---|---|---|---|---|---|---|
| Odczyt partii i publicznej historii | tak | tak | tak | tak | tak | tak | API: wyłącznie publiczne DTO |
| Utworzenie partii przez REST | nie | tak | tylko z OPERATOR_ROLE | nie | nie jako konto użytkownika | tylko z OPERATOR_ROLE | sesja + aktualna rola w API |
| Zgłoszenie raportu | nie | nie | nie | własny podpis + rola + certyfikat | tylko transport, nie autor raportu | nie bez roli stacji | API + kontrakt |
| Odczyt prywatnego statusu command | nie | własne | własne | własne | brak sesji przeglądarkowej | tak | sesja + owner lub Admin w API |
| Lista kandydatów QA | nie | nie bez roli QA | tak | nie | wewnętrzny worker | tak, odczyt administracyjny | API: aktualna rola QA lub Admin |
| Szczegóły jednego kandydata QA | nie | własne, jeśli jest owner | tak | własne | wewnętrzny worker | tak | API: rola QA/Admin albo owner |
| approveStage | nie | nie bez roli QA | tak | nie jako ta sama stacja raportu | nie bez roli QA | nie bez roli QA | kontrakt, aktualna QA_INSPECTOR_ROLE |
| completeStage | nie | nie | nie | dostarcza podpis, nie musi wysyłać tx | tak | nie bez RELAYER_ROLE | kontrakt i dowody stacji/QA |
| grantRole/revokeRole | nie | nie | nie | nie | nie | tak | AccessControl w kontrakcie |
| Wystawienie certyfikatu | nie | nie | nie | nie | nie | tylko jeśli ma odrębny klucz wystawcy | narzędzie wystawcy + allowlista |
| Rejestracja/odwołanie certyfikatu | nie | nie | nie | nie | nie | tak, kontrolowane narzędzie | backend/rejestr administracyjny, audyt |

QA nie otrzymuje przez samą rolę prawa do wszystkich prywatnych commands.
W kolejce QA widzi tylko dane potrzebne do decyzji, bez podpisów stacji,
signed tx, tokenów i stack trace. Status po potwierdzeniu może odczytać
z publicznej historii, a wewnętrzne błędy są dla właściciela/Admin.
Worker/indexer korzystają z wewnętrznych portów, nie publicznej sesji admina.

### Kilka ról na jednym adresie

Operator i operator QA mogą być tą samą osobą z dwiema rolami.
Stacja i operator QA dla tego samego raportu muszą mieć **różne adresy**.
Relayer i wystawca powinny mieć odrębne klucze od stacji i operatorów.
Środowisko testowe też nie może jednym kluczem „udawać” dwóch stron kontroli.

## Login - proponowany wariant MVP

SIWE (EIP-4361) dla EOA + serwerowa sesja. Nie projektujemy własnego
formatu „podpisz nonce” bez domeny i celu. SIWE używa podpisu wiadomości
EIP-191; StageCompletion pozostaje EIP-712. Nie mieszaj tych weryfikatorów.

### Przebieg

1. Klient podaje adres i środowisko na endpoint challenge.
2. Backend tworzy kryptograficznie losowy, jednorazowy nonce i zapamiętuje
   challenge powiązany z adresem, domeną, chainId, celem i datami.
3. Backend zwraca **pełną wiadomość SIWE do podpisania**, challengeId i expiry.
   Domenę/URI wybiera z konfiguracji, nie z dowolnych danych klienta ani
   niezaufanego nagłówka Host.
4. Operator podpisuje portfelem; symulator swoim kluczem stacji.
5. Backend parsuje wiadomość biblioteką SIWE, sprawdza jej dokładną zgodność
   z wystawionym challenge, podpis/recover, origin/URI, chainId, nonce,
   issuedAt, notBefore/expiry i zarejestrowany adres.
6. Zużycie challenge i utworzenie sesji są atomowe. Dwa równoległe loginy
   z tym samym challenge nie mogą oba się udać.
7. Powstaje losowy, nieprzewidywalny identyfikator sesji; uprawnienia
   są sprawdzane osobno przy każdej chronionej operacji.

Nonce challenge nie jest nonce stacji w kontrakcie ani nonce konta relayera.
Podpis challenge nie może być użyty jako StageCompletion lub certyfikat.
Przy błędnym podpisie obowiązuje rate limit, nie logowanie surowych sekretów.

### Transport sesji - propozycja do zatwierdzenia

Jedna cookie sesyjna dla przeglądarki i klienta Python:
- HttpOnly, Secure poza jawnym lokalnym HTTP, SameSite=Lax, Path=/,
  bez Domain i z prefiksem `__Host-` tam, gdzie obowiązuje HTTPS;
- dane sesji po stronie backendu, w trwałym magazynie umożliwiającym logout
  i unieważnienie; nie wkładaj roli do nieodwoływalnego tokenu na 8 godzin;
- Vite lokalnie proxy `/api` do backendu, a wdrożenie SPA/API pod jednym origin,
  żeby nie wprowadzać trzeciego wariantu cross-site cookies w MVP;
- Python przechowuje cookie w pamięci client cookie jar, nie w repozytorium.

Dla stanowiących sesję i pozostałych zapisów cookie-auth stosuj ochronę CSRF
zgodną ze Spring Security: token powiązany z kontekstem klienta i przesyłany
w nagłówku, weryfikacja Origin oraz dozwolonych źródeł. Dotyczy też
ustanowienia/wylogowania sesji i challenge login. Udostępnienie tokenu
CSRF klientowi nie może udostępniać HttpOnly session cookie.
Python pobiera token tą samą udokumentowaną ścieżką. CORS nie zastępuje CSRF.
Konkretne endpointy/nazwy nagłówków trafią do OpenAPI po zatwierdzeniu.

Proponowane ścieżki pozostają:
`POST /api/v1/auth/challenges`, `POST /api/v1/auth/sessions`,
`DELETE /api/v1/auth/sessions/current`.
Nie ma jeszcze implementacji ani kontraktu OpenAPI. Odczyt kontekstu bieżącej
sesji i sposób pobrania CSRF trzeba ująć w tym samym schemacie, nie zgadywać
osobno we frontendzie i symulatorze.

### Parametry robocze, nie zatwierdzone SLA

| Parametr | Propozycja dla demonstracji | Po wygaśnięciu |
|---|---|---|
| Challenge | 5 minut, jedno użycie | nowy challenge; brak przedłużania starego |
| Sesja | limit bezwzględny 8 godzin i 30 minut bezczynności | ponowny login |
| Role | ponowne sprawdzenie przed zapisem/udostępnieniem danych chronionych rolą | brak niejawnego pozwolenia z cache |
| Właścicielstwo command | trwały adres owner w zakresie deploymentu | nie zmienia się przy nowej sesji |

Wartości wymagają DEC-11. Nie używaj zegara przeglądarki jako źródła
ważności. Wyłączenie Secure wolno wprowadzić wyłącznie jawnym profilem local.

## Login stacji i podpisany POST

Nowy `POST stages` może być uwierzytelniony bez sesji **samym poprawnym
podpisem raportu**, z aktualną rolą i certyfikatem. Nie jest anonimowy:
owner pochodzi z recover, nie z transportu. Konieczne są limity rozmiaru,
rate limiting i pełna walidacja przed przyjęciem.
Cookie stacji nie zastępuje podpisu. Jeśli sesja jest obecna, musi odpowiadać
signerowi; rozbieżność jest jawnie odrzucana.

Prywatny `GET command` wymaga natomiast sesji właściciela lub Admin.
Stacja loguje się SIWE swoim kluczem, żeby odczytać status.
Publiczny paszport nie udostępnia statusu FAILED i błędów relayera.

Identyczny POST jest sposobem odtworzenia commandId po timeout, nie wejściem
do prywatnego statusu. Odpowiedź przyjęcia nie zawiera diagnostyki.
Po poprawnym dowodzie tożsamości i lookup porównuje podpisany zamiar:
- identyczny istniejący zamiar -> dotychczasowe ID, bez nowego pinowania;
- inna treść z tym samym nonce -> 409;
- nowy zamiar -> wszystkie bieżące sprawdzenia roli, certyfikatu i deadline.

Historyczny duplikat nie jest ponownym wykonaniem. Po blokadzie konta
z powodu kompromitacji nie udostępniaj odpowiedzi tylko na podstawie starego
podpisu; dostęp do audytu odzyskuje administrator. Szczegóły odwołania
certyfikatu opisuje [station-identity](station-identity.md).

## Odebranie uprawnienia i sytuacje niepewne

- Role są zakresowane przez chainId + adres kontraktu; sesja do innego
  środowiska nie przenosi uprawnień.
- Odebranie roli blokuje kolejne operacje wymagające tej roli, nawet jeśli
  cookie jeszcze nie wygasła. Nie musi usuwać prawa właściciela do własnej
  historycznej diagnostyki, o ile konto nie jest zablokowane.
- Kompromitacja klucza: blokada konta w backendzie, odwołanie jego sesji
  i certyfikatów oraz odebranie roli on-chain; to kilka kroków, nie ACID.
- Brak RPC/niepewność bieżącej roli: chroniony zapis nie jest dozwolony
  „na wszelki wypadek”; jawny 503. Kontrakt ponownie sprawdza role przy tx.
- Publiczne odczyty mogą pokazać ostatnią projekcję z informacją o aktualności;
  nie wolno oznaczać niezweryfikowanego stanu jako aktualnie potwierdzony.
- Proponowane HTTP: 401 brak/nieważna sesja, 403 brak prawa/zły dowód,
  404 nieznany zasób, 409 konflikt, 429 limit, 503 zależność niedostępna.
  Dla nieuprawnionego dostępu do cudzego command można konsekwentnie użyć 404,
  by nie ujawniać jego istnienia; wybór należy utrwalić w OpenAPI.

## Odbiór i podział prac

Osoba 2 implementuje sesje, CSRF, owner i role w API; osoba 1 role kontraktu;
osoba 3 login i zmianę konta/sieci w UI; osoba 4 login skryptowy i testy.
Zmiana konta w portfelu wymaga logout/relogin lub jawnego odrzucenia
niezgodnej sesji, nie tylko odświeżenia napisu w nagłówku.

Wymagane próby: challenge drugi raz, wygasły, inny adres/origin/chainId,
sesja po logout, cudzy commandId, brak CSRF, operator bez OPERATOR_ROLE,
QA bez dostępu do dowolnych commands, odebrana rola i timeout RPC,
podpis raportu użyty do loginu oraz login użyty jako podpis raportu.
