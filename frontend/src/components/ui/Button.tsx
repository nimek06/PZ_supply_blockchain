import type { ButtonHTMLAttributes } from 'react'

type Variant = 'primary' | 'secondary' | 'ghost'

const VARIANTS: Record<Variant, string> = {
  primary: 'bg-stone-900 text-white hover:bg-stone-700 disabled:bg-stone-300',
  secondary: 'border border-stone-300 bg-white text-stone-900 hover:bg-stone-100 disabled:text-stone-400',
  ghost: 'text-stone-600 hover:bg-stone-100 hover:text-stone-900 disabled:text-stone-300',
}

export function Button({ variant = 'primary', className = '', ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant }) {
  return (
    <button
      type="button"
      className={`inline-flex h-9 items-center justify-center rounded-md px-3 text-sm font-medium whitespace-nowrap transition-colors focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-stone-900 disabled:cursor-not-allowed ${VARIANTS[variant]} ${className}`}
      {...props}
    />
  )
}
