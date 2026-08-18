import { useCallback, useRef } from 'react'

/** Returns a monotonic request id and a guard for ignoring stale async responses. */
export function useLatestRequest() {
  const latestRequest = useRef(0)
  const nextRequestId = useCallback(() => {
    latestRequest.current += 1
    return latestRequest.current
  }, [])
  const isLatestRequest = useCallback(
    (requestId: number) => requestId === latestRequest.current,
    [],
  )
  return { nextRequestId, isLatestRequest }
}
