export const CANMAKAN_MASCOT_POSES = {
  wave: '/mascot/canmakan-mascot-wave.png',
  scan: '/mascot/canmakan-mascot-scan.png',
  safe: '/mascot/canmakan-mascot-safe.png',
  warning: '/mascot/canmakan-mascot-warning.png',
  unsafe: '/mascot/canmakan-mascot-unsafe.png',
} as const

export type CanMakanMascotPose = keyof typeof CANMAKAN_MASCOT_POSES
