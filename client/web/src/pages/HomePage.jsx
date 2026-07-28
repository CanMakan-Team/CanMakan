import PortalCard from '../components/PortalCard.jsx'

const portals = [
  {
    name: 'Family Admin Portal',
    description:
      'A future space for families to manage shared dietary preferences and product information.',
  },
  {
    name: 'System Admin Portal',
    description:
      'A future space for authorised administrators to operate the CanMakan platform.',
  },
]

function HomePage() {
  return (
    <main className="page-shell">
      <header className="hero">
        <p className="eyebrow">CanMakan</p>
        <h1>Web portals are being prepared.</h1>
        <p className="hero__summary">
          This initial React skeleton reserves a clear home for the family and
          system administration experiences.
        </p>
      </header>

      <section className="portal-grid" aria-label="Planned CanMakan portals">
        {portals.map((portal) => (
          <PortalCard key={portal.name} {...portal} />
        ))}
      </section>
    </main>
  )
}

export default HomePage
