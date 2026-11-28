import { useCallback, useEffect, useRef, useState } from 'react'

/** Small data-loading hook: runs `fn`, tracks loading/error, and exposes reload(). */
export function useLoader<T>(fn: () => Promise<T>) {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(true)

  // Keep the latest fn without making reload() change identity on every render.
  const fnRef = useRef(fn)
  useEffect(() => {
    fnRef.current = fn
  }, [fn])

  const reload = useCallback(async () => {
    setError('')
    try {
      setData(await fnRef.current())
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Could not load data')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    reload()
  }, [reload])

  return { data, error, loading, reload }
}
