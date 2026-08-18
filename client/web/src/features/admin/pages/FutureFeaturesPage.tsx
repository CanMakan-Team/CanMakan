const futureFeatures = [
  ['Assessment Review Queue', 'Manual review workflows are planned for a later release.'],
  ['Product Data Issues', 'Product correction tools are planned for a later release.'],
  ['Ingredient Alias Repository', 'Alias governance remains a future administrative capability.'],
  ['System Logs & Health', 'Operational telemetry and health views are not connected.'],
  ['AI Reasoning Review', 'Reasoning review is not represented as completed functionality.'],
  ['Application Usage Statistics', 'Distinct from the selected anonymised Consumer Trends feature.'],
]

export function FutureFeaturesPage() {
  return (
    <>
      <header className="page-header page-header--system">
        <div>
          <p className="eyebrow">Coming later</p>
          <h1>Future Features</h1>
          <p>
            These administration tools are planned but are not currently
            available.
          </p>
        </div>
      </header>
      <section className="future-grid">
        {futureFeatures.map(([title, description]) => (
          <article className="future-card" key={title}>
            <span className="status-badge status-badge--prototype">Future</span>
            <h2>{title}</h2>
            <p>{description}</p>
            <button className="button button--secondary" type="button" disabled>
              Not yet available
            </button>
          </article>
        ))}
      </section>
    </>
  )
}
