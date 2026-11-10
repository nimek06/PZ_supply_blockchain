# 5. Uprawnienia i logowanie

**Status:** projekt DEC-05/06 po zmianach [v2](changes-v2.md); zabezpieczenia nie
są jeszcze zaimplementowane. Wymagania RBAC wynikają z PDF. Uwierzytelnienie
API to SIWE tylko do wydania tokenu Bearer (bez cookie i CSRF).

## Tożsamość, uprawnienie i dowód operacji

- **Tożsamość:** adres, którego klucz podpisał login challenge, raport lub approval.
- **Sesja:** czasowy dostęp tego adresu do backendu (token Bearer).
- **Rola:** aktualne prawo do określonej operacji w danym wdrożeniu.
- **Podpis raportu / approval:** dowód autoryzacji konkretnego zdarzenia; nie token sesji.
- **Certyfikat i rejestracja stacji:** poświadczenie i wpis on-chain; nie rola operatora i nie hasło.

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
| Operator zmiany QA / QA_INSPECTOR_ROLE | AccessControl w kontrakcie | prywatni kandydaci kontroli i podpis `StageApproval` |
| Stacja / rejestr stacji | wpis on-chain (`registerStation`: aktywna, `expiresAt`, `stageMask`) **i** zgodny certyfikat w backendzie; brak STATION_ROLE | własne podpisane raporty i własne statusy |
| Relayer / RELAYER_ROLE | AccessControl w kontrakcie | createBatch i completeStage, nie decyzja operatora |
| Administrator / DEFAULT_ADMIN_ROLE | AccessControl w kontrakcie | nadawanie/odbieranie ról, rejestracja/odwołanie stacji, `maxDeadlineWindow` |
| Wystawca certyfikatu | zaufana lista publicznych kluczy w konfiguracji backendu (w MVP może to być klucz admina) | wystawianie poświadczeń stacji, bez automatycznych praw relayera/QA |

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
| Zgłoszenie raportu | nie | nie | nie | własny podpis + aktywna rejestracja stacji + certyfikat | tylko transport, nie autor raportu | nie bez rejestracji stacji | API + kontrakt |
| Odczyt prywatnego statusu command | nie | własne | własne | własne | brak sesji przeglądarkowej | tak | token + owner lub Admin w API |
| Lista kandydatów QA | nie | nie bez roli QA | tak | nie | wewnętrzny worker | tak, odczyt administracyjny | API: aktualna rola QA lub Admin |
| Szczegóły jednego kandydata QA | nie | własne, jeśli jest owner | tak | własne | wewnętrzny worker | tak | API: rola QA/Admin albo owner |
| Podpis `StageApproval` (POST approval) | nie | nie bez roli QA | tak | nie jako ta sama stacja raportu | nie | nie bez roli QA | API: token = adres podpisu, rola QA; kontrakt ponownie sprawdza rolę |
| completeStage | nie | nie | nie | dostarcza podpis, nie musi wysyłać tx | tak | nie bez RELAYER_ROLE | kontrakt i dowody stacji/QA |
| grantRole/revokeRole | nie | nie | nie | nie | nie | tak | AccessControl w kontrakcie |
| Wystawienie certyfikatu | nie | nie | nie | nie | nie | tylko jeśli ma odrębny klucz wystawcy | narzędzie wystawcy + allowlista |
| `registerStation` / `revokeStation` | nie | nie | nie | nie | nie | tak | kontrakt (DEFAULT_ADMIN_ROLE); backend śledzi zdarzenia |

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

## Login - wariant MVP (V2-06)

SIWE (EIP-4361) dla EOA, tylko do wydania tokenu Bearer. Nie projektujemy własnego
formatu „podpisz nonce” bez domeny i celu. SIWE używa podpisu wiadomości
EIP-191; StageCompletion i StageApproval pozostają EIP-712. Nie mieszaj weryfikatorów.

### Przebieg

1. Klient podaje adres i środowisko na endpoint challenge.
2. Backend tworzy kryptograficznie losowy, jednorazowy nonce i zapamiętuje
   challenge powiązany z adresem, domeną, chainId, celem i datami.
3. Backend zwraca **pełną wiadomość SIWE do podpisania**, challengeId i expiry.
   Domenę/URI wybiera z konfiguracji, nie z dowolnych danych klienta ani
   niezaufanego nagłówka Host.
4. Operator podpisuje portfelem; symulator swoim kluczem stacji.
5. Backend parsuje wiadomość biblioteką SIWE, sprawdza jej dokładną zgodność
   z wystawionym challenge, podpis/recover, domenę, chainId, nonce,
   issuedAt, notBefore/expiry i zarejestrowany adres.
6. Zużycie challenge i wydanie tokenu są atomowe. Dwa równoległe loginy
   z tym samym challenge nie mogą oba się udać.
7. Backend zwraca losowy token (co najmniej 256 bitów entropii). W bazie
   zapisuje wyłącznie jego skrót SHA-256; uprawnienia są sprawdzane osobno
   przy każdej chronionej operacji.

Nonce challenge nie jest nonce konta relayera (nonce stacji nie istnieje).
Podpis challenge nie może być użyty jako StageCompletion, StageApproval ani certyfikat.
Przy błędnym podpisie obowiązuje rate limit, nie logowanie surowych sekretów.

### Transport tokenu

- Nagłówek `Authorization: Bearer <token>`. Brak cookie, więc brak CSRF i
  wymogu `Secure`/`__Host-`; API działa po zwykłym HTTP lokalnie.
- Frontend trzyma token w pamięci aplikacji (nie w `localStorage`);
  po odświeżeniu strony wymagany jest ponowny login portfelem. Python trzyma
  token w zmiennej, nie w repozytorium ani logach.
- Dozwolone originy CORS to jawna lista z konfiguracji; wartość `*` jest zabroniona.
- Logout (`DELETE /api/v1/auth/sessions/current`) unieważnia token w bazie.
- Lokalny HTTPS jest potrzebny frontendowi do kamery/QR, nie API (V2-14).
- Ryzyko: wyciek tokenu przez XSS. Mitygacje: krótki TTL, CSP, brak
  wstrzykiwania HTML, zależności z lockfile.

Ścieżki: `POST /api/v1/auth/challenges`, `POST /api/v1/auth/sessions`,
`DELETE /api/v1/auth/sessions/current`. Nie ma jeszcze implementacji ani
kontraktu OpenAPI. Odczyt kontekstu bieżącej sesji trzeba ująć w tym samym
schemacie, nie zgadywać osobno we frontendzie i symulatorze.

### Parametry robocze (DEC-11), nie SLA

| Parametr | Propozycja dla demonstracji | Po wygaśnięciu |
|---|---|---|
| Challenge | 5 minut, jedno użycie | nowy challenge; brak przedłużania starego |
| Token | limit bezwzględny 8 godzin i 30 minut bezczynności | ponowny login |
| Role | ponowne sprawdzenie przed zapisem/udostępnieniem danych chronionych rolą | brak niejawnego pozwolenia z cache |
| Właścicielstwo command | trwały adres owner w zakresie deploymentu | nie zmienia się przy nowym tokenie |

Nie używaj zegara przeglądarki jako źródła ważności.

## Login stacji i podpisany POST

Nowy `POST stages` może być uwierzytelniony bez tokenu **samym poprawnym
podpisem raportu**, z aktywną rejestracją stacji i certyfikatem. Nie jest anonimowy:
owner pochodzi z recover, nie z transportu. Konieczne są limity rozmiaru,
rate limiting i pełna walidacja przed przyjęciem.
Token stacji nie zastępuje podpisu. Jeśli token jest obecny, musi odpowiadać
signerowi; rozbieżność jest jawnie odrzucana.

Prywatny `GET command` wymaga natomiast tokenu właściciela lub Admin.
Stacja loguje się SIWE swoim kluczem, żeby odczytać status.
Publiczny paszport nie udostępnia statusu FAILED i błędów relayera.

Identyczny POST jest sposobem odtworzenia commandId po timeout, nie wejściem
do prywatnego statusu. Odpowiedź przyjęcia nie zawiera diagnostyki.
Po poprawnym dowodzie tożsamości i lookup porównuje podpisany zamiar:
- identyczny istniejący zamiar (ten sam digest) -> dotychczasowe ID, bez nowego pinowania;
- inna treść dla etapu z aktywnym poleceniem -> 409 `STAGE_ALREADY_PENDING`;
- nowy zamiar -> wszystkie bieżące sprawdzenia rejestracji stacji, certyfikatu, deadline
  i [reguły zastępowania](corrections.md).

Historyczny duplikat nie jest ponownym wykonaniem. Po blokadzie konta
z powodu kompromitacji nie udostępniaj odpowiedzi tylko na podstawie starego
podpisu; dostęp do audytu odzyskuje administrator. Szczegóły odwołania
opisuje [station-identity](station-identity.md).

## Odebranie uprawnienia i sytuacje niepewne

- Role są zakresowane przez chainId + adres kontraktu; token do innego
  środowiska nie przenosi uprawnień.
- Odebranie roli blokuje kolejne operacje wymagające tej roli, nawet jeśli
  token jeszcze nie wygasł. Nie musi usuwać prawa właściciela do własnej
  historycznej diagnostyki, o ile konto nie jest zablokowane.
- Kompromitacja klucza stacji: `revokeStation` on-chain (kontrakt odrzuca kolejne
  `completeStage`), blokada konta w backendzie, odwołanie jego tokenów i
  uzgodnienie nierozstrzygniętych tx; to kilka kroków, nie ACID, ale blokada
  na łańcuchu jest jedna.
- Brak RPC/niepewność bieżącej roli: chroniony zapis nie jest dozwolony
  „na wszelki wypadek”; jawny 503. Kontrakt ponownie sprawdza role przy tx.
- Publiczne odczyty mogą pokazać ostatnią projekcję z informacją o aktualności;
  nie wolno oznaczać niezweryfikowanego stanu jako aktualnie potwierdzony.
- HTTP: 401 brak/nieważny token, 403 brak prawa/zły dowód,
  404 nieznany zasób, 409 konflikt, 429 limit, 503 zależność niedostępna.
  Dla nieuprawnionego dostępu do cudzego command można konsekwentnie użyć 404,
  by nie ujawniać jego istnienia; wybór należy utrwalić w OpenAPI.

## Odbiór i podział prac

Osoba 2 implementuje tokeny, owner i role w API; osoba 1 role i rejestr stacji w kontrakcie;
osoba 3 login i zmianę konta/sieci w UI; osoba 4 login skryptowy i testy.
Zmiana konta w portfelu wymaga logout/relogin lub jawnego odrzucenia
niezgodnego tokenu, nie tylko odświeżenia napisu w nagłówku.

Wymagane próby: challenge drugi raz, wygasły, inny adres/domena/chainId,
token po logout, cudzy commandId, operator bez OPERATOR_ROLE,
QA bez dostępu do dowolnych commands, odebrana rola i timeout RPC,
podpis raportu użyty do loginu oraz login użyty jako podpis raportu,
origin spoza listy CORS, podpis `StageApproval` użyty jako `StageCompletion`.
