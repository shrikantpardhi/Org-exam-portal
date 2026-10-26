"use client";

import { useId } from "react";

import { AnalyticsEmpty } from "./analytics-states";
import { FreshnessIndicator } from "./freshness-indicator";
import { formatValueWithUnit } from "./format";

/**
 * A bounded trend chart ({@code specs/17-P8-Analytics-V1.5.md §144, §145}).
 *
 * <p>No charting library is on the approved stack, so the chart is a plain SVG: it has no runtime
 * dependency, renders on the server-free client boundary, and every plotted point is a focusable
 * element with an accessible label. A tabular fallback is always rendered in a keyboard-operable
 * {@code <details>} element, so analytics never depends on visual interpretation alone.
 *
 * <p>The chart shows the metric name, its unit, the population or time scope in the description,
 * and the refresh timestamp.
 */
export interface TrendPoint {
  label: string;
  value: number | null;
}

export interface TrendChartProps {
  title: string;
  /** What is measured and the scope of the population. Required by {@code §144}. */
  description: string;
  unit: string;
  points: TrendPoint[];
  /** Fixed upper bound (for example 100 for a percentage, 1 for a difficulty index). */
  max?: number;
  lastCalculatedOn?: string | null;
  freshness?: string | null;
  emptyMessage?: string;
}

const CHART_WIDTH = 640;
const CHART_HEIGHT = 240;
const PAD = { top: 16, right: 16, bottom: 40, left: 56 };
const PLOT_WIDTH = CHART_WIDTH - PAD.left - PAD.right;
const PLOT_HEIGHT = CHART_HEIGHT - PAD.top - PAD.bottom;
const TICKS = [0, 0.25, 0.5, 0.75, 1];

function resolveMax(points: TrendPoint[], explicit?: number): number {
  if (explicit !== undefined && explicit > 0) {
    return explicit;
  }
  const values = points
    .map((point) => point.value)
    .filter((value): value is number => value !== null);
  const highest = values.length > 0 ? Math.max(...values) : 0;
  return highest > 0 ? highest : 1;
}

export function TrendChart({
  title,
  description,
  unit,
  points,
  max,
  lastCalculatedOn,
  freshness,
  emptyMessage,
}: TrendChartProps) {
  const titleId = useId();
  const maxValue = resolveMax(points, max);
  const plotted = points.filter((point) => point.value !== null);

  const xFor = (index: number): number => {
    if (points.length <= 1) {
      return PAD.left + PLOT_WIDTH / 2;
    }
    return PAD.left + (index / (points.length - 1)) * PLOT_WIDTH;
  };

  const yFor = (value: number): number => {
    const ratio = Math.min(Math.max(value / maxValue, 0), 1);
    return PAD.top + (1 - ratio) * PLOT_HEIGHT;
  };

  const summary =
    plotted.length === 0
      ? "No values"
      : plotted
          .map((point) => `${point.label} ${formatValueWithUnit(point.value, unit)}`)
          .join("; ");

  const showFreshness = lastCalculatedOn !== undefined || freshness !== undefined;

  return (
    <figure
      aria-labelledby={titleId}
      className="space-y-2 rounded-lg border border-border bg-surface p-4 shadow-sm"
    >
      <figcaption className="space-y-1">
        <div className="flex flex-wrap items-baseline justify-between gap-2">
          <h3 id={titleId} className="text-base font-semibold text-foreground">
            {title}
          </h3>
          <span className="rounded border border-border bg-muted px-2 py-0.5 text-xs text-muted-foreground">
            {unit === "%" ? "Percent" : unit ? `Unit: ${unit}` : "No unit"}
          </span>
        </div>
        <p className="text-sm text-muted-foreground">{description}</p>
      </figcaption>

      {points.length === 0 || plotted.length === 0 ? (
        <AnalyticsEmpty message={emptyMessage ?? "No chart data for this period."} />
      ) : (
        <>
          <svg
            viewBox={`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`}
            className="h-60 w-full"
            role="group"
            aria-label={`${title} chart. ${summary}`}
          >
            {TICKS.map((fraction) => {
              const y = PAD.top + (1 - fraction) * PLOT_HEIGHT;
              return (
                <g key={fraction} aria-hidden="true">
                  <line
                    x1={PAD.left}
                    y1={y}
                    x2={PAD.left + PLOT_WIDTH}
                    y2={y}
                    stroke="#e2e8f0"
                    strokeWidth={1}
                  />
                  <text
                    x={PAD.left - 8}
                    y={y + 4}
                    textAnchor="end"
                    fontSize={10}
                    fill="#64748b"
                  >
                    {formatValueWithUnit(maxValue * fraction, unit, 0)}
                  </text>
                </g>
              );
            })}

            <polyline
              fill="none"
              stroke="#2563eb"
              strokeWidth={2}
              points={points
                .map((point, index) =>
                  point.value === null ? null : `${xFor(index)},${yFor(point.value)}`,
                )
                .filter((coordinate): coordinate is string => coordinate !== null)
                .join(" ")}
            />

            {points.map((point, index) =>
              point.value === null ? null : (
                <circle
                  key={`${point.label}-${index}`}
                  cx={xFor(index)}
                  cy={yFor(point.value)}
                  r={4}
                  tabIndex={0}
                  role="img"
                  aria-label={`${point.label}: ${formatValueWithUnit(point.value, unit)}`}
                  className="fill-blue-600 focus:outline-none focus-visible:stroke-blue-300 focus-visible:stroke-[3]"
                >
                  <title>{`${point.label}: ${formatValueWithUnit(point.value, unit)}`}</title>
                </circle>
              ),
            )}
          </svg>

          <div className="flex justify-between text-xs text-muted-foreground" aria-hidden="true">
            <span>{points[0]?.label}</span>
            <span>{points[points.length - 1]?.label}</span>
          </div>
        </>
      )}

      {showFreshness ? (
        <FreshnessIndicator
          lastCalculatedOn={lastCalculatedOn ?? null}
          freshness={freshness ?? null}
        />
      ) : null}

      <details className="pt-1">
        <summary className="cursor-pointer rounded text-sm font-medium text-blue-700 underline focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600">
          View data table
        </summary>
        <div className="overflow-x-auto">
          <table className="mt-2 w-full text-left text-sm">
            <caption className="sr-only">{title}: data table</caption>
            <thead>
              <tr className="border-b border-border">
                <th scope="col" className="py-2">
                  Point
                </th>
                <th scope="col" className="py-2">
                  {unit === "%" ? "Value (percent)" : unit ? `Value (${unit})` : "Value"}
                </th>
              </tr>
            </thead>
            <tbody>
              {points.map((point, index) => (
                <tr key={`${point.label}-${index}`} className="border-b border-border">
                  <th scope="row" className="py-1 font-normal text-foreground">
                    {point.label}
                  </th>
                  <td className="py-1 tabular-nums">
                    {formatValueWithUnit(point.value, unit) ?? "No value"}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </figure>
  );
}
