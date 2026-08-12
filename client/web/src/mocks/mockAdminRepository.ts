import type { ConsumerTrendsResponse } from '../features/analytics/consumerTrendsTypes'
import { consumerTrends } from './mockData'

const delay = (milliseconds = 500) =>
  new Promise((resolve) => window.setTimeout(resolve, milliseconds))

export const mockAdminRepository = {
  async getConsumerTrends(): Promise<ConsumerTrendsResponse> {
    await delay(600)
    return structuredClone(consumerTrends)
  },
}
