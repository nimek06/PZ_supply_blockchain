// Długie hashe i adresy: zawijają się na telefonie zamiast rozpychać stronę.
export function Mono({ children }: { children: string }) {
  return <code className="font-mono text-xs break-all text-stone-600">{children}</code>
}
