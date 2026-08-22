"use strict";

function normalizeColor(color) {
  return String(color).replace(/^#/, "").toUpperCase();
}

function computeLabelSync(desiredLabels, currentLabels) {
  const desiredByName = new Map(desiredLabels.map((label) => [label.name, label]));
  const currentByName = new Map(currentLabels.map((label) => [label.name, label]));

  const toCreate = [];
  const toUpdate = [];
  const toDelete = [];

  for (const desired of desiredLabels) {
    const current = currentByName.get(desired.name);
    if (!current) {
      toCreate.push(desired);
      continue;
    }
    const colorChanged = normalizeColor(current.color) !== normalizeColor(desired.color);
    const descriptionChanged = (current.description || "") !== (desired.description || "");
    if (colorChanged || descriptionChanged) {
      toUpdate.push(desired);
    }
  }

  for (const current of currentLabels) {
    if (!desiredByName.has(current.name)) {
      toDelete.push(current);
    }
  }

  return { toCreate, toUpdate, toDelete };
}

module.exports = { computeLabelSync, normalizeColor };
