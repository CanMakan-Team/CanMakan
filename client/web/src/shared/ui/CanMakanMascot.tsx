import { CANMAKAN_MASCOT_POSES, type CanMakanMascotPose } from './canMakanMascotPoses'

export type { CanMakanMascotPose }

export type CanMakanMascotSize = 'compact' | 'banner' | 'medium' | 'large' | 'hero'

/**
 * Pose variants of the CanMakan mascot. Prefer semantic poses over reusing one image:
 * Wave for greetings/empty, Scan for scanner-style empty states, Safe/Warning/Unsafe for verdicts.
 */
export function CanMakanMascot({
  pose = 'wave',
  size = 'medium',
  alt = 'CanMakan mascot',
  className,
}: {
  pose?: CanMakanMascotPose
  size?: CanMakanMascotSize
  alt?: string
  className?: string
}) {
  const classes = ['canmakan-mascot', `canmakan-mascot--${size}`]
  if (className) classes.push(className)

  return (
    <img
      className={classes.join(' ')}
      src={CANMAKAN_MASCOT_POSES[pose]}
      alt={alt}
    />
  )
}

export function LoginBrand({ variant = 'family' }: { variant?: 'family' | 'system' }) {
  return (
    <div className={variant === 'system' ? 'login-brand login-brand--system' : 'login-brand'}>
      <span className="brand-mark" aria-hidden="true">
        CM
      </span>
      <strong>CanMakan</strong>
    </div>
  )
}
