import type { FlowStatus, SmithManeuverFlow } from '../api/types';
import { formatMoney } from '../lib/actions';
import { purposeMeta } from '../lib/cashTypes';

export const FLOW_STATUS: Record<FlowStatus, { label: string; tagClass: string }> = {
  TRACED: { label: 'Traced', tagClass: 'tag-sage' },
  PARTIALLY_TRACED: { label: 'Partially traced', tagClass: 'tag-butter' },
  UNTRACED: { label: 'Untraced', tagClass: 'tag-peach' },
};

interface FlowChainProps {
  flow: SmithManeuverFlow;
  onEdit: (flow: SmithManeuverFlow) => void;
}

export default function FlowChain({ flow, onEdit }: FlowChainProps) {
  const status = FLOW_STATUS[flow.status];

  return (
    <div className="card" style={{ marginBottom: '1.25rem' }}>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: '0.5rem',
        }}
      >
        <p className="card-title" style={{ margin: 0 }}>
          {flow.label}
        </p>
        <div style={{ display: 'flex', gap: '0.75rem', alignItems: 'center' }}>
          <span className={`tag ${status.tagClass}`}>{status.label}</span>
          <button type="button" className="btn btn-ghost" onClick={() => onEdit(flow)}>
            Edit
          </button>
        </div>
      </div>

      <div className="flow-chain">
        {flow.steps.map((step, index) => {
          const active = step.kind === 'SECURITY' || step.stepLabel === 'HELOC Draw';
          return (
            <div key={`${step.order}-${index}`} style={{ display: 'contents' }}>
              {index > 0 && <span className="flow-arrow">→</span>}
              <div className={`flow-step${active ? ' active' : ''}`}>
                <span className="flow-label">{step.stepLabel}</span>
                <span className="flow-amount">
                  {step.ticker ?? formatMoney(step.amount)}
                </span>
                {step.detail && (
                  <span
                    style={{
                      fontSize: '0.6875rem',
                      color: 'var(--text-muted)',
                      marginTop: '0.25rem',
                    }}
                  >
                    {step.detail}
                  </span>
                )}
                {step.purpose && (
                  <span
                    className={`tag ${purposeMeta(step.purpose).tagClass}`}
                    style={{ marginTop: '0.375rem', fontSize: '0.625rem' }}
                  >
                    {purposeMeta(step.purpose).label}
                  </span>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {flow.notes && (
        <p style={{ margin: '0.5rem 0 0', fontSize: '0.8125rem', color: 'var(--text-muted)' }}>
          {flow.notes}
        </p>
      )}
    </div>
  );
}
