import type { ReactNode } from "react";

export interface DonutSegment {
  value: number;
  /** CSS 색 값. 가이드 램프를 쓰려면 var(--color-purple-500) 형태로 넘긴다. */
  color: string;
}

interface DonutChartProps {
  segments: DonutSegment[];
  /** 세그먼트 합이 전체가 아닐 때 준다. 안 주면 세그먼트 합이 한 바퀴가 된다. */
  total?: number;
  size?: number;
  thickness?: number;
  trackColor?: string;
  rounded?: boolean;
  label: string;
  children?: ReactNode;
}

export function DonutChart({
  segments,
  total: totalProp,
  size = 140,
  thickness = 18,
  trackColor = "var(--color-purple-200)",
  rounded = false,
  label,
  children,
}: DonutChartProps) {
  const radius = (size - thickness) / 2;
  const circumference = 2 * Math.PI * radius;
  const total =
    (totalProp ?? segments.reduce((sum, s) => sum + s.value, 0)) || 1;

  const lengths = segments.map((s) => (s.value / total) * circumference);
  const arcs = segments.map((segment, index) => ({
    ...segment,
    length: lengths[index],
    offset: lengths.slice(0, index).reduce((sum, l) => sum + l, 0),
  }));

  return (
    <div
      className="relative shrink-0"
      style={{ width: size, height: size }}
      role="img"
      aria-label={label}
    >
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`}>
        <g transform={`rotate(-90 ${size / 2} ${size / 2})`}>
          <circle
            cx={size / 2}
            cy={size / 2}
            r={radius}
            fill="none"
            stroke={trackColor}
            strokeWidth={thickness}
          />
          {arcs.map((arc, index) => (
            <circle
              key={index}
              cx={size / 2}
              cy={size / 2}
              r={radius}
              fill="none"
              stroke={arc.color}
              strokeWidth={thickness}
              strokeLinecap={rounded ? "round" : "butt"}
              strokeDasharray={`${arc.length} ${circumference - arc.length}`}
              strokeDashoffset={-arc.offset}
            />
          ))}
        </g>
      </svg>

      {children && (
        <div className="absolute inset-0 flex flex-col items-center justify-center">
          {children}
        </div>
      )}
    </div>
  );
}
