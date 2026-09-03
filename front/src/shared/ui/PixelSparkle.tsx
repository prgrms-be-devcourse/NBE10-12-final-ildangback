import sparkle28 from "../../assets/illustrations/sparkle-group-28.webp";
import sparkle31 from "../../assets/illustrations/sparkle-group-31.webp";

export function PixelSparkle({ className = "" }: { className?: string }) {
  return (
    <div
      className={`relative flex h-8 w-10 shrink-0 items-center justify-end ${className}`}
    >
      {/* 4-pixel small sparkle (lower left) */}
      <img
        src={sparkle31}
        alt=""
        aria-hidden
        className="absolute bottom-0.5 left-0.5 h-3.5 object-contain pixelated"
      />
      {/* 5-pixel large sparkle (upper right) */}
      <img
        src={sparkle28}
        alt=""
        aria-hidden
        className="absolute top-0 right-0 h-5.5 object-contain pixelated"
      />
    </div>
  );
}
