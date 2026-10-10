// The desktop parser must give the same answers as the phone's: both run desktop/test/parser-cases.json.
import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const PlanParser = require("../src/parser.js");
const cases = JSON.parse(readFileSync(new URL("./parser-cases.json", import.meta.url), "utf8"));

for (const [i, c] of cases.entries()) {
  test(`${i} ${c.fn}: ${c.input.split("\n")[0].slice(0, 30)}`, () => {
    if (c.fn === "splitNote") {
      assert.deepEqual(PlanParser.splitNote(c.input).map(({ title, category, minuteOfDay, location }) => ({ title, category, minuteOfDay, location })), c.expect);
    } else if (c.fn === "parseShare") {
      const { title, location, category } = PlanParser.parseShare(c.input);
      assert.deepEqual({ title, location, category }, c.expect);
    } else {
      assert.equal(PlanParser[c.fn](c.input), c.expect);
    }
  });
}

test("Google Maps place links give the name", () => {
  assert.equal(PlanParser.nameFromMapsUrl("https://www.google.com/maps/place/%E6%98%8E%E6%B4%9E%E9%A4%83%E5%AD%90+%E6%9C%AC%E5%BA%97/@37.56,126.98,17z"), "明洞餃子 本店");
  assert.equal(PlanParser.parseShare("https://www.google.com/maps/place/Gyeongbokgung+Palace/@37.57,126.97").title, "Gyeongbokgung Palace");
});
