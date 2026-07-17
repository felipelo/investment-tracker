import { Link, useLocation, useSearchParams } from 'react-router-dom';
import { usePortfolioContext } from '../context/PortfolioContext';

const TYPE_TAG: Record<string, string> = {
  Taxable: 'tag-peach',
  TFSA: 'tag-sky',
  RRSP: 'tag-butter',
  'Smith Maneuver': 'tag-lavender',
  Other: 'tag-sage',
};

function typeTagClass(type: string | null): string {
  return (type && TYPE_TAG[type]) || 'tag-sage';
}

export default function PortfolioSwitcher() {
  const location = useLocation();
  const [searchParams, setSearchParams] = useSearchParams();
  const {
    portfolios,
    activePortfolioId,
    activePortfolio,
    setActivePortfolioId,
    isPending,
    isError,
  } = usePortfolioContext();
  const isDashboard = location.pathname === '/dashboard';
  const isOverall = isDashboard && searchParams.get('view') === 'all';

  if (isPending) {
    return <span className="context-status">Loading portfolios…</span>;
  }

  if (isError) {
    return <span className="context-status negative">Could not load portfolios.</span>;
  }

  if (portfolios.length === 0) {
    return (
      <span className="context-status">
        No portfolio. <Link to="/portfolios">Create one</Link>
      </span>
    );
  }

  return (
    <div className="portfolio-switcher">
      <span className={`tag ${isOverall ? 'tag-sage' : typeTagClass(activePortfolio?.type ?? null)}`}>
        {isOverall ? 'All' : activePortfolio?.type ?? 'Other'}
      </span>
      <select
        aria-label="Active portfolio"
        value={isOverall ? 'all' : (activePortfolioId ?? '')}
        onChange={(event) => {
          if (event.target.value === 'all') {
            setSearchParams({ view: 'all' });
            return;
          }
          setActivePortfolioId(Number(event.target.value));
          if (isDashboard) {
            setSearchParams({});
          }
        }}
      >
        {isDashboard && <option value="all">ALL</option>}
        {portfolios.map((portfolio) => (
          <option key={portfolio.id} value={portfolio.id}>
            {portfolio.name}
          </option>
        ))}
      </select>
    </div>
  );
}
