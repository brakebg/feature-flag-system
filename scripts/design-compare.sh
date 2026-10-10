#!/usr/bin/env bash
# Spec 11.6 "Matching the design" 3: a review gallery that shows each screen's screenshot next to
# its design PNG at the same size. No automated pixel comparison against the designs.
# Input: the committed Playwright baselines (chromium-desktop). Output: docs/design-compare/.
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=docs/design-compare
SNAP=frontend/e2e/screenshots.spec.ts-snapshots
mkdir -p "$OUT"
screens=("1 · Sign in:1-sign-in" "2 · Flags workspace:2-flags-workspace" "3 · New flag dialog:3-new-flag-dialog" \
  "4 · Delete group confirmation:4-delete-group-confirmation" "5 · Audit log:5-audit-log")
{
  cat <<'HTML'
<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Design compare</title>
<style>
  body { margin: 0; padding: 24px; font: 14px/1.4 system-ui, sans-serif; background: #f4f5f7; color: #15181d; }
  h1 { font-size: 22px; margin: 0 0 8px; }
  p { margin: 0 0 24px; color: #3b424c; }
  section { margin-bottom: 40px; }
  h2 { font-size: 16px; margin: 0 0 12px; }
  .pair { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
  figure { margin: 0; background: #fff; border: 1px solid #dfe3e8; border-radius: 8px; padding: 8px; }
  figcaption { font-size: 12px; color: #5b6470; margin-bottom: 6px; }
  img { display: block; width: 100%; aspect-ratio: 1440 / 900; object-fit: contain; }
  @media (max-width: 900px) { .pair { grid-template-columns: 1fr; } }
</style>
</head>
<body>
<h1>Design compare</h1>
<p>Built UI (Playwright baseline, chromium-desktop 1440 × 900; times and usernames masked) next to the approved design. Names are sample data.</p>
HTML
  for s in "${screens[@]}"; do
    title=${s%%:*}; file=${s##*:}
    cp "$SNAP/$file-chromium-desktop-linux.png" "$OUT/$file-built.png"
    design=$(printf '%s' "$title.png" | sed 's/ /%20/g; s/·/%C2%B7/g')
    printf '<section>\n<h2>%s</h2>\n<div class="pair">\n' "$title"
    printf '<figure><figcaption>Built</figcaption><img src="%s-built.png" alt="Built: %s"></figure>\n' "$file" "$title"
    printf '<figure><figcaption>Design</figcaption><img src="../design/%s" alt="Design: %s"></figure>\n' "$design" "$title"
    printf '</div>\n</section>\n'
  done
  printf '</body>\n</html>\n'
} > "$OUT/index.html"
echo "wrote $OUT/index.html"
