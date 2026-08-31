import logoGommit from "../../assets/illustrations/logo-gommit.webp";

export function Logo({ className = "" }: { className?: string }) {
  return (
    <img
      src={logoGommit}
      alt="Go!mmit"
      className={`h-9 object-contain pixelated ${className}`}
    />
  );
}
