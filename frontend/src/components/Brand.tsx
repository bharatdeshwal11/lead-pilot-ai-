import { Navigation } from 'lucide-react'
import { Link } from 'react-router-dom'

export function Brand({ link = true }: { link?: boolean }) {
  const contents = <><span className="brand-icon"><Navigation size={20} strokeWidth={2.4} /></span><span>LeadPilot<span className="brand-ai"> AI</span></span></>
  return link
    ? <Link className="brand" to="/" aria-label="LeadPilot AI home">{contents}</Link>
    : <div className="brand">{contents}</div>
}
