function PortalCard({ name, description }) {
  return (
    <article className="portal-card">
      <p className="portal-card__status">Planned portal</p>
      <h2>{name}</h2>
      <p>{description}</p>
    </article>
  )
}

export default PortalCard
