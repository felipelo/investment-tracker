import { Navigate, Route, Routes } from 'react-router-dom';
import AppShell from './components/AppShell';
import PlaceholderPage from './pages/PlaceholderPage';
import RecordTradePage from './pages/RecordTradePage';
import HoldingsPage from './pages/HoldingsPage';
import PortfoliosPage from './pages/PortfoliosPage';
import AccountsPage from './pages/AccountsPage';
import DashboardPage from './pages/DashboardPage';
import DividendsPage from './pages/DividendsPage';
import CashTransactionsPage from './pages/CashTransactionsPage';
import SmithManeuverPage from './pages/SmithManeuverPage';

export default function App() {
  return (
    <AppShell>
      <Routes>
        <Route path="/" element={<Navigate to="/record-trade" replace />} />
        <Route path="/record-trade" element={<RecordTradePage />} />
        <Route path="/cash-transactions" element={<CashTransactionsPage />} />
        <Route path="/dividends" element={<DividendsPage />} />
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/holdings" element={<HoldingsPage />} />
        <Route path="/portfolios" element={<PortfoliosPage />} />
        <Route path="/accounts" element={<AccountsPage />} />
        <Route path="/smith-maneuver" element={<SmithManeuverPage />} />
        <Route
          path="/tax-summary"
          element={
            <PlaceholderPage title="Tax summary" subtitle="Tax year export view" />
          }
        />
        <Route path="*" element={<Navigate to="/record-trade" replace />} />
      </Routes>
    </AppShell>
  );
}
