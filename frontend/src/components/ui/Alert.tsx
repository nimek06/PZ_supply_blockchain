import type { ReactNode } from 'react'
import type { Tone } from './tone'

const BARS: Record<Tone, string> = {
  neutral: 'border-l-stone-400',
  pending: 'border-l-sky-500',
  success: 'border-l-emerald-600',
  warning: 'border-l-amber-500',
  danger: 'border-l-rose-600',
}

export function Alert({ tone = 'neutral', title, children }: { tone?: Tone; title: string; children?: ReactNode }) {
  return (
    <div
      role={tone === 'danger' ? 'alert' : 'status'}
      className={`rounded-md border border-stone-200 border-l-4 bg-white px-4 py-3 text-sm ${BARS[tone]}`}
    >
      <p className="font-medium">{title}</p>
      {children && <div className="mt-1 text-stone-600">{children}</div>}
    </div>
  )
}
