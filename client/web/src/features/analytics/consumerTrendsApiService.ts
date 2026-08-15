import { mockAdminRepository } from '../../mocks/mockAdminRepository'
import { apiRequest, useMockApi } from '../../shared/api/apiClient'
import type {
  ConsumerTrendsQuery,
  ConsumerTrendsResponse,
} from './consumerTrendsTypes'

export const consumerTrendsEndpoint = '/api/admin/consumer-trends'

function buildConsumerTrendsPath(query: ConsumerTrendsQuery): string {
  const parameters = new URLSearchParams()
  if (query.from !== undefined) parameters.set('from', query.from)
  if (query.to !== undefined) parameters.set('to', query.to)
  if (query.limit !== undefined) parameters.set('limit', String(query.limit))
  if (query.category !== undefined) parameters.set('category', query.category)

  const queryString = parameters.toString()
  return queryString
    ? `${consumerTrendsEndpoint}?${queryString}`
    : consumerTrendsEndpoint
}

export const consumerTrendsApiService = {
  getConsumerTrends(
    query: ConsumerTrendsQuery = {},
  ): Promise<ConsumerTrendsResponse> {
    if (useMockApi) return mockAdminRepository.getConsumerTrends(query)
    return apiRequest<ConsumerTrendsResponse>(buildConsumerTrendsPath(query))
  },
}
