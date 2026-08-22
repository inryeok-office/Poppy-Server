"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const { computeLabelSync } = require("../labels");

test("승인된 라벨이 없으면 생성 대상에 포함된다", () => {
  const desired = [{ name: "feature", color: "1D76DB", description: "새로운 기능 작업" }];
  const current = [];
  const { toCreate, toUpdate, toDelete } = computeLabelSync(desired, current);
  assert.equal(toCreate.length, 1);
  assert.equal(toCreate[0].name, "feature");
  assert.equal(toUpdate.length, 0);
  assert.equal(toDelete.length, 0);
});

test("색상 또는 설명이 다르면 갱신 대상에 포함된다", () => {
  const desired = [{ name: "bug", color: "D73A4A", description: "버그 수정 작업" }];
  const current = [{ name: "bug", color: "ff0000", description: "다른 설명" }];
  const { toCreate, toUpdate, toDelete } = computeLabelSync(desired, current);
  assert.equal(toCreate.length, 0);
  assert.equal(toUpdate.length, 1);
  assert.equal(toDelete.length, 0);
});

test("색상과 설명이 동일하면 갱신 대상이 아니다(대소문자 무시)", () => {
  const desired = [{ name: "bug", color: "D73A4A", description: "버그 수정 작업" }];
  const current = [{ name: "bug", color: "d73a4a", description: "버그 수정 작업" }];
  const { toUpdate } = computeLabelSync(desired, current);
  assert.equal(toUpdate.length, 0);
});

test("승인 목록에 없는 기존 라벨은 삭제 대상에 포함된다", () => {
  const desired = [{ name: "feature", color: "1D76DB", description: "" }];
  const current = [
    { name: "feature", color: "1D76DB", description: "" },
    { name: "wontfix", color: "ffffff", description: "" },
  ];
  const { toDelete } = computeLabelSync(desired, current);
  assert.equal(toDelete.length, 1);
  assert.equal(toDelete[0].name, "wontfix");
});

test("동일 입력을 다시 계산해도 같은 결과가 나온다(멱등성)", () => {
  const desired = [
    { name: "feature", color: "1D76DB", description: "새로운 기능 작업" },
    { name: "bug", color: "D73A4A", description: "버그 수정 작업" },
  ];
  const current = [...desired];
  const result = computeLabelSync(desired, current);
  assert.equal(result.toCreate.length, 0);
  assert.equal(result.toUpdate.length, 0);
  assert.equal(result.toDelete.length, 0);
});
