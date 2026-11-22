import { QRCodeSVG } from 'qrcode.react'
import { passportUrl } from './parseBatchQr'

// Etykieta partii: QR z publicznym adresem paszportu. Bez podpisów, kluczy i tokenów.
export function BatchQrCode({ batchNumber, appOrigin }: { batchNumber: string; appOrigin: string }) {
  const url = passportUrl(batchNumber, appOrigin)
  return (
    <figure className="flex flex-col items-center gap-2">
      <QRCodeSVG value={url} size={136} marginSize={2} title={`Kod QR paszportu ${batchNumber}`} />
      <figcaption className="text-center text-xs break-all text-slate-500">{url}</figcaption>
    </figure>
  )
}
