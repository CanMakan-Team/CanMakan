import type { ConsumerTrendResponse } from '../shared/api/types'
import { consumerTrends } from './mockData'

const delay = (milliseconds = 500) =>
  new Promise((resolve) => window.setTimeout(resolve, milliseconds))

export const mockAdminRepository = {
  async getConsumerTrends(): Promise<ConsumerTrendResponse> {
    await delay(600)
    return structuredClone(consumerTrends)
  },
}
