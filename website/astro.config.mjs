import { defineConfig } from "astro/config";
import sitemap from "@astrojs/sitemap";

// GitHub Pages project site base path.
export default defineConfig({
  site: "https://alphagps.app",
  base: "/",
  integrations: [sitemap()]
});


