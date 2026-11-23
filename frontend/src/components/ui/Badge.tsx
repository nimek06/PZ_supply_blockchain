import type { ReactNode } from 'react'
import type { Tone } from './tone'

const DOTS: Record<Tone, string> = {
  neutral: 'bg-stone-400',
  pending: 'bg-sky-500',
  success: 'bg-emerald-600',
  warning: 'bg-amber-500',
  danger: 'bg-rose-600',
}

// Status zawsze ma tekst, kropka w kolorze tylko go wzmacnia.
export function Badge({ tone = 'neutral', children }: { tone?: Tone; children: ReactNode }) {
  return (
    <span className="inline-flex items-center gap-1.5 rounded-full border border-stone-200 bg-white px-2 py-0.5 text-xs font-medium text-stone-700">
      <span aria-hidden className={`size-1.5 rounded-full ${DOTS[tone]}`} />
      {children}
    </span>
  )
}
