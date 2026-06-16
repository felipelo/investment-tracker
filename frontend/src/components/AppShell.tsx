import { NavLink } from 'react-router-dom';
import type { ReactNode } from 'react';

interface NavItem {
  to: string;
  label: string;
}

const sections: { title: string; items: NavItem[] }[] = [
  {
    title: 'Overview',
    items: [
      { to: '/dashboard', label: 'Dashboard' },
      { to: '/holdings', label: 'Holdings' },
      { to: '/portfolios', label: 'Portfolios' },
    ],
  },
  {
    title: 'Records',
    items: [
      { to: '/record-trade', label: 'Record trade' },
      { to: '/smith-maneuver', label: 'Smith Maneuver' },
      { to: '/tax-summary', label: 'Tax summary' },
    ],
  },
];

export default function AppShell({ children }: { children: ReactNode }) {
  return (
    <div className="app-shell">
      <aside className="sidebar">
        <div className="sidebar-brand">
          <div className="brand-mark">IT</div>
          <div>
            <div className="brand-text">Investment Tracker</div>
            <div className="brand-sub">Local · CAD</div>
          </div>
        </div>
        <nav>
          {sections.map((section) => (
            <div key={section.title}>
              <div className="nav-section">{section.title}</div>
              {section.items.map((item) => (
                <NavLink
                  key={item.to}
                  to={item.to}
                  className={({ isActive }) =>
                    isActive ? 'nav-link active' : 'nav-link'
                  }
                >
                  {item.label}
                </NavLink>
              ))}
            </div>
          ))}
        </nav>
      </aside>
      <main className="main">{children}</main>
    </div>
  );
}
