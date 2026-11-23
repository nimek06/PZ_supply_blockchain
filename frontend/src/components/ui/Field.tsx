import type { ReactNode } from 'react'

// Lista "etykieta: wartość" w szczegółach partii i polecenia.
export function Fields({ children }: { children: ReactNode }) {
  return <dl className="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-[10rem_1fr]">{children}</dl>
}

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <>
      <dt className="text-stone-500">{label}</dt>
      <dd className="min-w-0">{children}</dd>
    </>
  )
}
