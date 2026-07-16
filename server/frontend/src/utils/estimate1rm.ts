// Port wzoru z mobilnego Estimate1RMUseCase.kt (parytet edycji sesji web ↔ app).
// Klasyczna relacja mean velocity -> %1RM wg González-Badillo, interpolacja liniowa
// z klamrą na końcach.
const VELOCITY_TO_PERCENT_1RM: [number, number][] = [
  [1.0, 70],
  [0.75, 80],
  [0.5, 90],
  [0.3, 100],
]

/** %1RM odpowiadający średniej prędkości koncentrycznej. */
export function percentOf1RM(meanVelocityMs: number): number {
  const p = VELOCITY_TO_PERCENT_1RM
  if (meanVelocityMs >= p[0][0]) return p[0][1]
  if (meanVelocityMs <= p[p.length - 1][0]) return p[p.length - 1][1]
  for (let i = 0; i < p.length - 1; i++) {
    const [vHigh, pctHigh] = p[i]
    const [vLow, pctLow] = p[i + 1]
    if (meanVelocityMs <= vHigh && meanVelocityMs >= vLow) {
      const t = (meanVelocityMs - vLow) / (vHigh - vLow)
      return pctLow + t * (pctHigh - pctLow)
    }
  }
  return p[p.length - 1][1]
}

/**
 * Estymacja 1RM z ciężaru i średniej prędkości powtórzenia. Zwraca null, gdy dane
 * nie pozwalają na sensowną estymację (brak ciężaru / prędkości).
 */
export function estimate1rmFromVelocity(loadKg: number, meanVelocityMs: number): number | null {
  if (loadKg <= 0 || meanVelocityMs <= 0) return null
  return loadKg / (percentOf1RM(meanVelocityMs) / 100)
}
