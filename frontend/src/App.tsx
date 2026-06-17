import { Navigate, Route, Routes } from 'react-router-dom';
import AppShell from './components/AppShell';
import PlaceholderPage from './pages/PlaceholderPage';
import RecordTradePage from './pages/RecordTradePage';
import HoldingsPage from './pages/HoldingsPage';
import PortfoliosPage from './pages/PortfoliosPage';

export default function App() {
  return (
    <AppShell>
      <Routes>
        <Route path="/" element={<Navigate to="/record-trade" replace />} />
        <Route path="/record-trade" element={<RecordTradePage />} />
        <Route
          path="/dashboard"
          element={
            <PlaceholderPage
              title="Dashboard"
              subtitle="Portfolio overview and returns"
            />
          }
        />
        <Route path="/holdings" element={<HoldingsPage />} />
        <Route path="/portfolios" element={<PortfoliosPage />} />
        <Route
          path="/smith-maneuver"
          element={
            <PlaceholderPage
              title="Smith Maneuver"
              subtitle="HELOC flow tracing"
            />
          }
        />
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
