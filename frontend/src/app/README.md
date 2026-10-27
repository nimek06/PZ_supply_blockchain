# Start, routing i konteksty

**Języki:** TypeScript/TSX, CSS.

Plan: `App.tsx`, `router.tsx`, `providers.tsx`, `styles.css`.
Tutaj łącz QueryClientProvider, konfigurację Wagmi i RainbowKit (`projectId` WalletConnect/Reown) oraz router.
QueryClient ma być stabilny, nie tworzony przy każdym renderze.

Planowane widoki: publiczny `/passport/:batchNumber`, operator `/operator`
i inspektor `/qa`. Widoki pochodzą z features, a nie z dużego App.tsx.
Obsłuż route not found, granicę błędu i globalne komunikaty.

Publiczny routing nie może wymuszać portfela. Osłona widoku QA jest tylko UX;
autoryzację robi backend/kontrakt. Globalne CSS i theme nie zawierają
logiki etapów ani klienta HTTP.
