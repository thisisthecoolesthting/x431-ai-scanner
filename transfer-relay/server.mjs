#!/usr/bin/env node
/**
 * Free self-hosted upload drop for Together Car Works tablet exports.
 * No external API keys. Pair with app mode "Your server".
 */
import http from "node:http";
import fs from "node:fs";
import path from "node:path";
import { createHash } from "node:crypto";

const PORT = Number(process.env.TCW_PORT || 8765);
const INBOX = path.resolve(process.env.TCW_INBOX || "./inbox");
fs.mkdirSync(INBOX, { recursive: true });

function freeBytes() {
  try {
    const st = fs.statfsSync(INBOX);
    return st.bfree * st.bsize;
  } catch {
    return 0;
  }
}

function health(res) {
  res.writeHead(200, { "Content-Type": "application/json" });
  res.end(
    JSON.stringify({
      savePath: INBOX,
      freeBytes: freeBytes(),
      version: "tcw-relay-1",
    }),
  );
}

const server = http.createServer((req, res) => {
  const url = new URL(req.url || "/", `http://localhost`);
  if (req.method === "GET" && url.pathname === "/health") return health(res);

  if (url.pathname === "/upload" && (req.method === "POST" || req.method === "PATCH" || req.method === "HEAD")) {
    const name = url.searchParams.get("name") || `upload-${Date.now()}.zip`;
    const dest = path.join(INBOX, path.basename(name));
  if (req.method === "HEAD") {
      const have = fs.existsSync(dest) ? fs.statSync(dest).size : 0;
      res.writeHead(200, { "X-TCW-Have": String(have) });
      return res.end();
    }
    const offset = Number(url.searchParams.get("offset") || 0);
    const chunks = [];
    req.on("data", (c) => chunks.push(c));
    req.on("end", () => {
      const buf = Buffer.concat(chunks);
      if (offset > 0 && fs.existsSync(dest)) {
        const fd = fs.openSync(dest, "a");
        fs.writeSync(fd, buf, 0, buf.length, offset);
        fs.closeSync(fd);
      } else {
        fs.writeFileSync(dest, buf);
      }
      const sha = url.searchParams.get("sha256");
      if (sha) {
        const got = createHash("sha256").update(fs.readFileSync(dest)).digest("hex");
        if (got !== sha) {
          res.writeHead(422);
          return res.end("sha256 mismatch");
        }
      }
      res.writeHead(200, { "Content-Type": "application/json" });
      res.end(JSON.stringify({ path: dest, bytes: fs.statSync(dest).size }));
    });
    return;
  }

  res.writeHead(404);
  res.end("not found");
});

server.listen(PORT, "0.0.0.0", () => {
  console.log(`TCW relay listening on :${PORT} → ${INBOX}`);
});
