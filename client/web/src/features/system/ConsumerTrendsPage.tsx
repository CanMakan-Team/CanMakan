import { useCallback, useEffect, useMemo, useState } from 'react'
import { adminService } from '../../api/adminService'
import { getErrorMessage } from '../../api/apiErrors'
import type { ConsumerTrendResponse } from '../../api/types'
import { EmptyState, ErrorState, LoadingState } from '../../components/PageState'
import { StatusBadge } from '../../components/StatusBadge'

export function ConsumerTrendsPage() {
  const [data, setData] = useState<ConsumerTrendResponse | null>(null)
  const [period, setPeriod] = useState('30')
  const [category, setCategory] = useState('ALL')
  const [platform, setPlatform] = useState('ALL')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const load = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      setData(await adminService.getConsumerTrends())
    } catch (caughtError) {
      setError(getErrorMessage(caughtError))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    const timeoutId = window.setTimeout(() => void load(), 0)
    return () => window.clearTimeout(timeoutId)
  }, [load, period, platform])

  const categories = useMemo(
    () =>
      category === 'ALL'
        ? data?.productCategories
        : data?.productCategories?.filter((item) => item.category === category),
    [category, data],
  )
  const totalVerdicts =
    data?.verdictDistribution.reduce((sum, item) => sum + item.count, 0) ?? 0
  const maxIngredient = Math.max(
    ...(data?.flaggedIngredients.map((item) => item.count) ?? [1]),
  )

  return (
    <>
      <header className="page-header page-header--system">
        <div>
          <p className="eyebrow">Feature 7 · anonymised aggregate data</p>
          <h1>Consumer Trends</h1>
          <p>
            Aggregated scan and assessment patterns only. Named users, families,
            emails and individual dietary profiles are excluded.
          </p>
        </div>
      </header>

      <section className="filter-bar filter-bar--system" aria-label="Trend controls">
        <div className="field-group">
          <label htmlFor="trend-period">Reporting period</label>
          <select id="trend-period" value={period} onChange={(event) => setPeriod(event.target.value)}>
            <option value="7">Last 7 days</option>
            <option value="30">Last 30 days</option>
            <option value="90">Last 90 days</option>
          </select>
        </div>
        <div className="field-group">
          <label htmlFor="trend-category">Product category</label>
          <select id="trend-category" value={category} onChange={(event) => setCategory(event.target.value)}>
            <option value="ALL">All categories</option>
            {data?.productCategories?.map((item) => (
              <option key={item.category} value={item.category}>{item.category}</option>
            ))}
          </select>
        </div>
        <div className="field-group">
          <label htmlFor="trend-platform">Platform scope</label>
          <select id="trend-platform" value={platform} onChange={(event) => setPlatform(event.target.value)}>
            <option value="ALL">All supported platforms</option>
            <option value="ANDROID">Android</option>
            <option value="WEB">Web assessment records</option>
          </select>
        </div>
      </section>

      {loading ? (
        <LoadingState label="Generating anonymised consumer trends…" />
      ) : error ? (
        <ErrorState message={error} onRetry={load} />
      ) : !data || totalVerdicts === 0 ? (
        <EmptyState
          title="No aggregate data"
          description="There is no anonymised assessment data for this reporting period."
        />
      ) : (
        <>
          {data.partial && (
            <div className="notice notice--warning">
              <strong>Partial aggregate data</strong>
              <p>
                Some category and platform dimensions are not supplied by the
                mock dataset. Counts shown remain aggregate and anonymised.
              </p>
            </div>
          )}
          <div className="trend-grid">
            <section className="panel" aria-labelledby="verdict-chart-title">
              <div className="panel__header">
                <div>
                  <p className="eyebrow">Assessment outcomes</p>
                  <h2 id="verdict-chart-title">Verdict distribution</h2>
                </div>
                <strong>{totalVerdicts.toLocaleString()} assessments</strong>
              </div>
              <div className="donut-layout">
                <div
                  className="donut-chart"
                  role="img"
                  aria-label="Verdict distribution chart. Full values are available in the adjacent table."
                >
                  <span>{totalVerdicts.toLocaleString()}<small>Total</small></span>
                </div>
                <table className="compact-table">
                  <caption>Accessible verdict distribution values</caption>
                  <thead><tr><th>Verdict</th><th>Count</th><th>Percent</th></tr></thead>
                  <tbody>
                    {data.verdictDistribution.map((item) => (
                      <tr key={item.verdict}>
                        <td><StatusBadge status={item.verdict} /></td>
                        <td>{item.count.toLocaleString()}</td>
                        <td>{Math.round((item.count / totalVerdicts) * 100)}%</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>

            <section className="panel" aria-labelledby="ingredient-chart-title">
              <p className="eyebrow">Resolved names</p>
              <h2 id="ingredient-chart-title">Most commonly flagged ingredients</h2>
              <div className="bar-chart" aria-hidden="true">
                {data.flaggedIngredients.map((item) => (
                  <div key={item.resolvedIngredient}>
                    <span>{item.resolvedIngredient}</span>
                    <i style={{ width: `${(item.count / maxIngredient) * 100}%` }} />
                    <strong>{item.count}</strong>
                  </div>
                ))}
              </div>
              <table className="compact-table accessible-equivalent">
                <caption>Accessible flagged ingredient values</caption>
                <thead><tr><th>Resolved ingredient</th><th>Assessments</th><th>Share of flagged list</th></tr></thead>
                <tbody>
                  {data.flaggedIngredients.map((item) => (
                    <tr key={item.resolvedIngredient}>
                      <td>{item.resolvedIngredient}</td>
                      <td>{item.count}</td>
                      <td>
                        {Math.round(
                          (item.count /
                            data.flaggedIngredients.reduce(
                              (sum, ingredient) => sum + ingredient.count,
                              0,
                            )) *
                            100,
                        )}%
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </section>
          </div>

          <section className="panel panel--table">
            <div className="panel__header">
              <div>
                <p className="eyebrow">Optional third view</p>
                <h2>Verdicts by product category</h2>
              </div>
            </div>
            <div className="responsive-table">
              <table className="data-table">
                <caption>Aggregate verdict counts by product category</caption>
                <thead>
                  <tr><th>Category</th><th>Safe</th><th>Warning</th><th>Avoid</th><th>Incomplete</th></tr>
                </thead>
                <tbody>
                  {categories?.map((item) => (
                    <tr key={item.category}>
                      <th scope="row">{item.category}</th>
                      <td>{item.safeCount}</td>
                      <td>{item.warningCount}</td>
                      <td>{item.avoidCount}</td>
                      <td>{item.incompleteCount}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </section>
        </>
      )}
    </>
  )
}
