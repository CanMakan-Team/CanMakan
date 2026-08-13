#!/usr/bin/env node
/**
 * Replaces hardcoded #hex / matching rgba() in app.css with token CSS vars.
 * Run after colors.json includes the web palette: node design-tokens/migrate-web-css.mjs
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { spawnSync } from "node:child_process";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const tokensPath = path.join(__dirname, "colors.json");
const cssPath = path.join(root, "client/web/src/styles/app.css");

const tokens = JSON.parse(fs.readFileSync(tokensPath, "utf8"));

function normalizeHex(h) {
  let s = String(h).replace("#", "").toLowerCase();
  if (s.length === 3) s = s.split("").map((c) => c + c).join("");
  if (s.length === 8) s = s.slice(0, 6);
  return ("#" + s).toUpperCase();
}

function parseHex(hex) {
  const clean = normalizeHex(hex).slice(1);
  return {
    r: parseInt(clean.slice(0, 2), 16),
    g: parseInt(clean.slice(2, 4), 16),
    b: parseInt(clean.slice(4, 6), 16),
  };
}

const hexToCss = new Map();
const rgbToCss = new Map();
for (const c of tokens.colors) {
  if (!c.css) continue;
  const key = normalizeHex(c.hex);
  if (!hexToCss.has(key)) hexToCss.set(key, c.css);
  const { r, g, b } = parseHex(c.hex);
  const rgbKey = `${r},${g},${b}`;
  if (!rgbToCss.has(rgbKey)) rgbToCss.set(rgbKey, c.css);
}

let css = fs.readFileSync(cssPath, "utf8");
let hexReplacements = 0;
css = css.replace(/#([0-9A-Fa-f]{3,8})\b/g, (match) => {
  const v = hexToCss.get(normalizeHex(match));
  if (!v) return match;
  hexReplacements += 1;
  return `var(${v})`;
});

let rgbaReplacements = 0;
css = css.replace(
  /rgba\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*([0-9.]+)\s*\)/g,
  (match, r, g, b, a) => {
    const v = rgbToCss.get(`${Number(r)},${Number(g)},${Number(b)}`);
    if (!v) return match;
    const alpha = Number(a);
    rgbaReplacements += 1;
    if (alpha >= 0.999) return `var(${v})`;
    const pct = Math.round(alpha * 1000) / 10;
    return `color-mix(in srgb, var(${v}) ${pct}%, transparent)`;
  },
);

fs.writeFileSync(cssPath, css, "utf8");
console.log(`Updated ${path.relative(root, cssPath)}`);
console.log(`  hex → var: ${hexReplacements}`);
console.log(`  rgba → color-mix/var: ${rgbaReplacements}`);

const leftoverHex = [...css.matchAll(/#([0-9A-Fa-f]{3,8})\b/g)].map((m) => m[0]);
const leftoverRgba = [...css.matchAll(/rgba?\([^)]+\)/g)].map((m) => m[0]);
console.log(`  leftover #hex: ${leftoverHex.length}`);
console.log(`  leftover rgb/rgba: ${leftoverRgba.length}`);
if (leftoverHex.length) console.log("  leftover hex:", leftoverHex.join(", "));
if (leftoverRgba.length) console.log("  leftover rgba:", leftoverRgba.join(", "));

const gen = spawnSync(process.execPath, [path.join(__dirname, "generate.mjs")], {
  cwd: root,
  stdio: "inherit",
});
process.exit(gen.status ?? 1);
