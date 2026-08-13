#!/usr/bin/env node
/**
 * Generates Compose Color.kt and web tokens.css from colors.json.
 * Usage (from repo root): node design-tokens/generate.mjs
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(__dirname, "..");
const tokensPath = path.join(__dirname, "colors.json");

const composeOut = path.join(
  root,
  "client/mobile/app/src/main/java/sg/edu/nus/iss/canmakan/shared/ui/theme/Color.kt",
);
const cssOut = path.join(root, "client/web/src/styles/tokens.css");

const tokens = JSON.parse(fs.readFileSync(tokensPath, "utf8"));

function parseHex(hex) {
  const clean = hex.replace("#", "").toUpperCase();
  if (clean.length !== 6) {
    throw new Error(`Expected 6-digit hex, got ${hex}`);
  }
  return {
    r: parseInt(clean.slice(0, 2), 16),
    g: parseInt(clean.slice(2, 4), 16),
    b: parseInt(clean.slice(4, 6), 16),
    rgb: clean,
  };
}

function composeArgb(hex, alpha = 1) {
  const { rgb } = parseHex(hex);
  const a = Math.round(Math.min(1, Math.max(0, alpha)) * 255)
    .toString(16)
    .toUpperCase()
    .padStart(2, "0");
  return `0x${a}${rgb}`;
}

function cssValue(hex, alpha) {
  if (alpha == null || alpha >= 1) {
    return hex.toUpperCase();
  }
  const { r, g, b } = parseHex(hex);
  const a = Number(alpha.toFixed(3));
  return `rgba(${r}, ${g}, ${b}, ${a})`;
}

function generateCompose(colors) {
  const lines = [
    "package sg.edu.nus.iss.canmakan.shared.ui.theme",
    "",
    "import androidx.compose.ui.graphics.Color",
    "",
    "// GENERATED FILE — do not edit by hand.",
    "// Source: design-tokens/colors.json",
    "// Regenerate: node design-tokens/generate.mjs",
    "",
  ];

  for (const color of colors) {
    if (!color.compose) continue;
    if (color.comment) {
      lines.push(`/** ${color.comment} */`);
    }
    const argb = composeArgb(color.hex, color.alpha ?? 1);
    lines.push(`val ${color.compose} = Color(${argb})`);
  }

  lines.push("");
  return lines.join("\n");
}

function generateCss(colors, aliases) {
  const lines = [
    "/* GENERATED FILE — do not edit by hand. */",
    "/* Source: design-tokens/colors.json */",
    "/* Regenerate: node design-tokens/generate.mjs */",
    "",
    ":root {",
  ];

  for (const color of colors) {
    if (!color.css) continue;
    if (color.comment) {
      lines.push(`  /* ${color.comment} */`);
    }
    lines.push(`  ${color.css}: ${cssValue(color.hex, color.alpha)};`);
  }

  lines.push("");
  lines.push("  /* Legacy web aliases (keep existing class CSS working) */");
  for (const [alias, target] of Object.entries(aliases)) {
    lines.push(`  ${alias}: var(${target});`);
  }

  lines.push("  --shadow: 0 12px 36px rgba(25, 65, 53, 0.08);");
  lines.push("}");
  lines.push("");
  return lines.join("\n");
}

const composeSource = generateCompose(tokens.colors);
const cssSource = generateCss(tokens.colors, tokens.cssAliases ?? {});

fs.writeFileSync(composeOut, composeSource, "utf8");
fs.writeFileSync(cssOut, cssSource, "utf8");

console.log(`Wrote ${path.relative(root, composeOut)}`);
console.log(`Wrote ${path.relative(root, cssOut)}`);
