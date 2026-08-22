"use strict";

const API_BASE = "https://api.github.com";

function parseNextLink(linkHeader) {
  if (!linkHeader) return null;
  const parts = linkHeader.split(",");
  for (const part of parts) {
    const match = part.match(/<([^>]+)>;\s*rel="next"/);
    if (match) return match[1];
  }
  return null;
}

function createGitHubClient({ token, repository, apiBase = API_BASE }) {
  if (!token) throw new Error("GITHUB_TOKEN is required");
  if (!repository) throw new Error("repository (owner/repo) is required");

  const headers = {
    Authorization: `Bearer ${token}`,
    Accept: "application/vnd.github+json",
    "X-GitHub-Api-Version": "2022-11-28",
  };

  async function request(method, path, body) {
    const url = path.startsWith("http") ? path : `${apiBase}${path}`;
    const response = await fetch(url, {
      method,
      headers: body === undefined ? headers : { ...headers, "Content-Type": "application/json" },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    const text = await response.text();
    const data = text ? JSON.parse(text) : null;
    if (!response.ok) {
      const error = new Error(`GitHub API ${method} ${path} failed: ${response.status} ${response.statusText}`);
      error.status = response.status;
      error.body = data;
      throw error;
    }
    return data;
  }

  async function paginate(path) {
    let results = [];
    let nextUrl = `${apiBase}${path}${path.includes("?") ? "&" : "?"}per_page=100`;
    while (nextUrl) {
      const response = await fetch(nextUrl, { headers });
      if (!response.ok) {
        throw new Error(`GitHub API GET ${nextUrl} failed: ${response.status} ${response.statusText}`);
      }
      const pageData = await response.json();
      results = results.concat(pageData);
      nextUrl = parseNextLink(response.headers.get("link"));
    }
    return results;
  }

  return {
    get: (path) => request("GET", path),
    post: (path, body) => request("POST", path, body),
    patch: (path, body) => request("PATCH", path, body),
    put: (path, body) => request("PUT", path, body),
    del: (path) => request("DELETE", path),
    paginate,
    repository,
  };
}

module.exports = { createGitHubClient, parseNextLink };
