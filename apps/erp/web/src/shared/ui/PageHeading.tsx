import { createContext, useContext, useEffect } from 'react'
import { useLocation } from 'react-router-dom'

export type HeadingMeta = { path: string; title: string; metricLabel?: string; metricValue?: string; metricCaption?: string }
export const PageHeadingContext = createContext<(heading: HeadingMeta) => void>(() => {})

export function PageHeading({ title, metricLabel, metricValue, metricCaption }: Omit<HeadingMeta, 'path'>) {
  const publish = useContext(PageHeadingContext)
  const path = useLocation().pathname
  useEffect(() => { publish({ path, title, metricLabel, metricValue, metricCaption }) }, [publish, path, title, metricLabel, metricValue, metricCaption])
  return null
}
