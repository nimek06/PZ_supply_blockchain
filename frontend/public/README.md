# Statyczne pliki publiczne

**Formaty:** ikony, favicon, ewentualnie manifest PWA i robots.txt.

Vite kopiuje te pliki bez przetwarzania. Wszystko tutaj jest publiczne.
Zasoby importowane przez komponenty należą do src/assets.
Nie umieszczaj danych partii, raportów, tokenów i konfiguracji serwera.

README jest instrukcją zespołu, nie zasobem aplikacji: przed publikacją
skonfiguruj krok buildu/pakowania wykluczający go z publicznego outputu.
Manifest/service worker dodaj dopiero przy implementowaniu PWA.
