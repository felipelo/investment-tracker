interface PlaceholderPageProps {
  title: string;
  subtitle: string;
}

export default function PlaceholderPage({ title, subtitle }: PlaceholderPageProps) {
  return (
    <>
      <header className="page-header">
        <div>
          <h1 className="page-title">{title}</h1>
          <p className="page-subtitle">{subtitle}</p>
        </div>
      </header>
      <div className="card" style={{ maxWidth: 640 }}>
        <p className="card-title">Coming soon</p>
        <p style={{ margin: 0, color: 'var(--text-muted)' }}>
          This screen is part of the design but not built yet. The first working
          slice is <strong>Record trade</strong>.
        </p>
      </div>
    </>
  );
}
