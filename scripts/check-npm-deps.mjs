#!/usr/bin/env node
// Gate 4 (spec 11.3): no UI component library on the frontend (MUI, Ant, Chakra,
// Bootstrap). Checks direct and transitive packages in frontend/package-lock.json.
// Locked list: never shorten it (CLAUDE.md section 3).
import { readFileSync } from 'node:fs';

const banned = [
  { name: 'mui', re: /^(@mui\/|@material-ui\/|material-ui$|mui$)/ },
  { name: 'antd', re: /^(antd$|antd-|@ant-design\/)/ },
  { name: 'chakra', re: /^(@chakra-ui\/|chakra-ui)/ },
  { name: 'bootstrap', re: /^(bootstrap$|react-bootstrap$|bootstrap-|@popperjs\/bootstrap|reactstrap$)/ },
];

const pkg = JSON.parse(readFileSync('frontend/package.json', 'utf8'));
const lock = JSON.parse(readFileSync('frontend/package-lock.json', 'utf8'));
const names = new Set([
  ...Object.keys(pkg.dependencies ?? {}),
  ...Object.keys(pkg.devDependencies ?? {}),
  ...Object.keys(lock.packages ?? {})
    .filter((p) => p.includes('node_modules/'))
    .map((p) => p.slice(p.lastIndexOf('node_modules/') + 'node_modules/'.length)),
]);

const hits = [];
for (const n of names) for (const b of banned) if (b.re.test(n)) hits.push(`${n} (${b.name})`);
if (hits.length) {
  console.error(`Banned UI component libraries found:\n${hits.map((h) => `  - ${h}`).join('\n')}`);
  process.exit(1);
}
console.log(`check-npm-deps: ${names.size} packages, no banned UI library`);
