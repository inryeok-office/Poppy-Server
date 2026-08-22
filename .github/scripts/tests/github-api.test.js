"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { parseNextLink } = require("../github-api");

test("Link 헤더에서 다음 페이지 URL을 추출한다", () => {
  const link =
    '<https://api.github.com/repos/org/repo/labels?page=2>; rel="next", <https://api.github.com/repos/org/repo/labels?page=5>; rel="last"';
  assert.equal(parseNextLink(link), "https://api.github.com/repos/org/repo/labels?page=2");
});

test("next 관계가 없으면 null을 반환한다", () => {
  const link = '<https://api.github.com/repos/org/repo/labels?page=1>; rel="prev"';
  assert.equal(parseNextLink(link), null);
});

test("헤더가 없으면 null을 반환한다", () => {
  assert.equal(parseNextLink(null), null);
  assert.equal(parseNextLink(undefined), null);
});
