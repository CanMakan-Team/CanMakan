import { useState } from 'react'

export const CONSUMER_TRENDS_ROWS_PER_PAGE = 10

export function usePagedItems<T>(items: T[], resetKey: string) {
  const [page, setPage] = useState(0)
  const [pageResetKey, setPageResetKey] = useState(resetKey)
  if (pageResetKey !== resetKey) {
    setPageResetKey(resetKey)
    setPage(0)
  }

  const totalPages = Math.max(1, Math.ceil(items.length / CONSUMER_TRENDS_ROWS_PER_PAGE))
  const safePage = Math.min(page, totalPages - 1)
  const start = safePage * CONSUMER_TRENDS_ROWS_PER_PAGE
  return {
    page: safePage,
    setPage,
    start,
    visible: items.slice(start, start + CONSUMER_TRENDS_ROWS_PER_PAGE),
    rangeEnd: Math.min(start + CONSUMER_TRENDS_ROWS_PER_PAGE, items.length),
    total: items.length,
  }
}
