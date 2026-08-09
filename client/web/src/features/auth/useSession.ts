import { useContext } from 'react'
import { SessionContext } from './SessionContext'

/** Use session
 * 
 * @author Amelia
 */

export function useSession() {
  // Get session context
  const context = useContext(SessionContext)

  // If no context, throw an error
  if (!context) {
    throw new Error('useSession must be used within SessionProvider')
  }
  return context
}
