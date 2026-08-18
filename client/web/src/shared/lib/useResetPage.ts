import { useState, type Dispatch, type SetStateAction } from 'react'

/** Keeps a page index at 0 whenever the caller’s reset key changes. */
export function useResetPage(
  resetKey: string,
): [number, Dispatch<SetStateAction<number>>] {
  const [page, setPage] = useState(0)
  const [storedKey, setStoredKey] = useState(resetKey)
  if (storedKey !== resetKey) {
    setStoredKey(resetKey)
    setPage(0)
  }
  return [page, setPage]
}
