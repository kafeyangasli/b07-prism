import { copyFile, mkdir } from "node:fs/promises";

const outputDirectory = new URL("../src/main/resources/static/vendor/", import.meta.url);

await mkdir(outputDirectory, { recursive: true });
await copyFile(
  new URL("../node_modules/htmx.org/dist/htmx.min.js", import.meta.url),
  new URL("htmx.min.js", outputDirectory),
);
