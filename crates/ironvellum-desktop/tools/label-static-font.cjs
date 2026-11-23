// Correct the stale Thin names left by hb-subset when freezing the weight axis.
// Usage: node label-static-font.cjs <original.ttf> <instanced input.ttf> <output.ttf>
const fs = require("node:fs");
const assert = require("node:assert/strict");
const [originalPath, input, output] = process.argv.slice(2);
const original = fs.readFileSync(originalPath);
const source = fs.readFileSync(input);
function getTable(font, tag) {
  for (let i = 0; i < font.readUInt16BE(4); i++) {
    const p = 12 + i * 16;
    if (font.toString("ascii", p, p + 4) === tag) {
      const start = font.readUInt32BE(p + 8);
      return font.subarray(start, start + font.readUInt32BE(p + 12));
    }
  }
  throw Error("Missing " + tag);
}
// The prescribed HarfBuzz invocation retains every glyph and its original ID.
// Restore the original complete cmap, including legacy Macintosh subtables.
assert.equal(getTable(source, "maxp").readUInt16BE(4), getTable(original, "maxp").readUInt16BE(4));
const count = source.readUInt16BE(4);
const header = Buffer.from(source.subarray(0, 12 + 16 * count));
const parts = [header];
let offset = header.length;
let adjustmentOffset;

function checksum(bytes) {
  let sum = 0;
  for (let p = 0; p < bytes.length; p += 4)
    sum = (sum + bytes.readUInt32BE(p)) >>> 0;
  return sum;
}

function regularNames(table) {
  assert.equal(table.readUInt16BE(0), 0, "expected name table format 0");
  const records = table.readUInt16BE(2);
  const start = table.readUInt16BE(4);
  const directory = Buffer.from(table.subarray(0, start));
  const strings = [];
  let used = 0;
  for (let i = 0; i < records; i++) {
    const p = 6 + 12 * i;
    const id = table.readUInt16BE(p + 6);
    const first = start + table.readUInt16BE(p + 10);
    let bytes = table.subarray(first, first + table.readUInt16BE(p + 8));
    if ([1, 3, 4, 6, 17].includes(id)) {
      assert.equal(table.readUInt16BE(p), 3, "expected Windows Unicode label");
      const old = Buffer.from(bytes).swap16().toString("utf16le");
      assert.ok(old.includes("Thin"));
      const label = id === 1 ? old.replace(" Thin", "") : old.replace("Thin", "Regular");
      bytes = Buffer.from(label, "utf16le").swap16();
    }
    directory.writeUInt16BE(bytes.length, p + 8);
    directory.writeUInt16BE(used, p + 10);
    strings.push(bytes);
    used += bytes.length;
  }
  return Buffer.concat([directory, ...strings]);
}

for (let i = 0; i < count; i++) {
  const p = 12 + i * 16;
  const tag = header.toString("ascii", p, p + 4);
  const first = source.readUInt32BE(p + 8);
  let table = Buffer.from(source.subarray(first, first + source.readUInt32BE(p + 12)));
  if (tag === "name") table = regularNames(table);
  if (tag === "cmap") table = getTable(original, tag);
  if (tag === "head") {
    table.writeUInt32BE(0, 8);
    adjustmentOffset = offset + 8;
  }
  const padded = Buffer.alloc(Math.ceil(table.length / 4) * 4);
  table.copy(padded);
  header.writeUInt32BE(checksum(padded), p + 4);
  header.writeUInt32BE(offset, p + 8);
  header.writeUInt32BE(table.length, p + 12);
  parts.push(padded);
  offset += padded.length;
}
const font = Buffer.concat(parts);
font.writeUInt32BE((0xb1b0afba - checksum(font)) >>> 0, adjustmentOffset);
assert.equal(checksum(font), 0xb1b0afba);
fs.writeFileSync(output, font);
