#!/usr/bin/env node
// Node port of run-platform.ps1; uses only Node built-ins.
import { spawn, spawnSync } from "node:child_process";
import { existsSync, readdirSync, statSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const build = process.argv.includes("--build");

const secret = process.env.JWT_SECRET;
if (!secret || secret.trim().length < 32) {
  console.error("JWT_SECRET must be set in the environment and contain at least 32 characters.");
  process.exit(1);
}

const allDefinitions = [
  { name: "identity", directory: "identity-service", artifact: "identity-service", port: 8081, env: {} },
  { name: "catalog", directory: "catalog-service", artifact: "catalog-service", port: 8082, env: {} },
  { name: "inventory", directory: "inventory-service", artifact: "inventory-service", port: 8083, env: {} },
  { name: "cart", directory: "cart-service", artifact: "cart-service", port: 8084, env: { CATALOG_URL: "http://localhost:8082" } },
  { name: "payment", directory: "payment-service", artifact: "payment-service", port: 8086, env: {} },
  {
    name: "order", directory: "order-service", artifact: "order-service", port: 8085,
    env: {
      CART_URL: "http://localhost:8084",
      INVENTORY_URL: "http://localhost:8083",
      PAYMENT_URL: "http://localhost:8086",
    },
  },
  {
    name: "platform-ui", directory: "platform-ui", artifact: "platform-ui", port: 8090,
    env: {
      IDENTITY_URL: "http://localhost:8081",
      CATALOG_URL: "http://localhost:8082",
      INVENTORY_URL: "http://localhost:8083",
      CART_URL: "http://localhost:8084",
      ORDER_URL: "http://localhost:8085",
      PAYMENT_URL: "http://localhost:8086",
    },
  },
];

// This repository has no platform-ui module; skip any definition whose folder is absent.
const definitions = allDefinitions.filter((d) => {
  const present = existsSync(join(root, d.directory));
  if (!present) console.warn(`WARNING: Skipping ${d.name}: folder '${d.directory}' not found.`);
  return present;
});

function findServiceJar(def) {
  const target = join(root, def.directory, "target");
  if (!existsSync(target)) {
    throw new Error(`Missing build directory: ${target}. Build ${def.name} first.`);
  }
  const jars = readdirSync(target)
    .filter((f) => f.startsWith(`${def.artifact}-`) && f.endsWith(".jar") && !f.startsWith("original-"))
    .map((f) => ({ path: join(target, f), mtime: statSync(join(target, f)).mtimeMs }))
    .sort((a, b) => b.mtime - a.mtime);
  if (jars.length === 0) {
    throw new Error(`Missing executable JAR for ${def.name} in ${target}. Run Maven package first.`);
  }
  return jars[0].path;
}

try {
  if (build) {
    for (const def of definitions) {
      console.log(`Building ${def.name}...`);
      const result = spawnSync("mvn", ["-B", "-ntp", "-q", "package"], {
        cwd: join(root, def.directory),
        stdio: "inherit",
      });
      if (result.status !== 0) throw new Error(`Maven build failed for ${def.name}.`);
    }
  }

  for (const def of definitions) {
    const jar = findServiceJar(def);
    const child = spawn("java", ["-jar", jar], {
      cwd: join(root, def.directory),
      env: { ...process.env, SERVER_PORT: String(def.port), ...def.env },
      stdio: "ignore",
      detached: true,
    });
    child.unref();
    console.log(`Started ${def.name} on http://localhost:${def.port} (PID ${child.pid})`);
  }
} catch (err) {
  console.error(err.message);
  process.exit(1);
}

console.log("");
console.log("Open http://localhost:8090 for the Commerce Control Center.");
console.log("The browser defaults to Demo mode; switch to Live API after the services are healthy.");
console.log("Stop the listed Java processes with `kill <PID>` when finished.");
