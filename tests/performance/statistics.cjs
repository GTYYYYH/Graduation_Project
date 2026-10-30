'use strict';

function nearestRank(values, quantile) {
  if (!Array.isArray(values) || values.length === 0 || !values.every(Number.isFinite)) throw new TypeError('A nonempty array of finite samples is required');
  if (!(quantile > 0 && quantile <= 1)) throw new RangeError('Quantile must be in (0, 1]');
  const sorted = [...values].sort((a, b) => a - b);
  return sorted[Math.ceil(quantile * sorted.length) - 1];
}

function frameStats(timestamps) {
  if (!Array.isArray(timestamps) || timestamps.length < 2 || !timestamps.every(Number.isFinite)) throw new TypeError('At least two finite timestamps are required');
  const intervals = timestamps.slice(1).map((t, i) => t - timestamps[i]);
  if (intervals.some(t => t <= 0)) throw new RangeError('Timestamps must increase');
  const durationMs = timestamps.at(-1) - timestamps[0];
  return { frames: intervals.length, durationMs, fps: intervals.length * 1000 / durationMs, p95IntervalMs: nearestRank(intervals, .95) };
}
module.exports = { nearestRank, frameStats };
