import wavePng from '@mascot/canmakan_mascot_wave.png'
import scanPng from '@mascot/canmakan_mascot_scan.png'
import safePng from '@mascot/canmakan_mascot_safe.png'
import warningPng from '@mascot/canmakan_mascot_warning.png'
import unsafePng from '@mascot/canmakan_mascot_unsafe.png'

export const CANMAKAN_MASCOT_POSES = {
  wave: wavePng,
  scan: scanPng,
  safe: safePng,
  warning: warningPng,
  unsafe: unsafePng,
} as const

export type CanMakanMascotPose = keyof typeof CANMAKAN_MASCOT_POSES
