import { setZXingModuleOverrides } from '@yudiel/react-qr-scanner'
import wasmUrl from 'zxing-wasm/reader/zxing_reader.wasm?url'

// Domyślnie zxing-wasm pobiera plik .wasm z CDN (jsdelivr). Podajemy kopię z naszego builda,
// żeby skaner działał bez internetu i nie wysyłał zapytań do obcych serwerów.
setZXingModuleOverrides({
  locateFile: (path: string, prefix: string) => (path.endsWith('.wasm') ? wasmUrl : prefix + path),
})
