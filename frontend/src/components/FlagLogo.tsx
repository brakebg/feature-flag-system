/** The logo mark of spec 005 4.5 (decorative): a primary circle with an apricot corner. */
export function FlagLogo({ size = 28 }: { size?: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 28 28" aria-hidden="true">
      <circle cx="12" cy="16" r="12" fill="#3D59A8" />
      <path d="M16 0a12 12 0 0 1 12 12H16z" fill="#E8A87C" />
    </svg>
  );
}
