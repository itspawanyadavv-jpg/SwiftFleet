import { describe, expect, it } from 'vitest';
import { formatDistance, haversineMeters } from './geo';

describe('geo helpers', () => {
  it('computes 0m for identical points and realistic meters for nearby drop', () => {
    expect(haversineMeters(28.646705, 77.444478, 28.646705, 77.444478)).toBeCloseTo(0, 3);
    const meters = haversineMeters(28.646705, 77.444478, 28.647605, 77.444478);
    expect(meters).toBeGreaterThan(95);
    expect(meters).toBeLessThan(105);
  });

  it('formats meters and kilometers cleanly', () => {
    expect(formatDistance(142)).toBe('142 m');
    expect(formatDistance(1450)).toBe('1.45 km');
  });
});

