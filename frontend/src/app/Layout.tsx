import { NavLink, Outlet } from 'react-router'
import { WalletPanel } from './WalletPanel'

const linkClass = ({ isActive }: { isActive: boolean }) =>
  `border-b-2 px-1 py-1 text-sm ${isActive ? 'border-stone-900 text-stone-900' : 'border-transparent text-stone-500 hover:text-stone-900'}`

export function Layout() {
  return (
    <div className="min-h-dvh">
      <header className="border-b border-stone-200 bg-white">
        <div className="mx-auto flex max-w-4xl flex-col gap-3 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
          <nav className="flex flex-wrap items-center gap-x-5 gap-y-2" aria-label="Główna nawigacja">
            <NavLink to="/" end className="text-sm font-semibold tracking-tight">
              E-bike · łańcuch dostaw
            </NavLink>
            <NavLink to="/operator" className={linkClass}>
              Operator
            </NavLink>
            <NavLink to="/qa" className={linkClass}>
              Kontrola QA
            </NavLink>
          </nav>
          <WalletPanel />
        </div>
      </header>
      <main className="mx-auto max-w-4xl px-4 py-8">
        <Outlet />
      </main>
    </div>
  )
}
