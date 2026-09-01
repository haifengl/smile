const fs = require("fs");
const path = require("path");
const dedent = require("./scripts/dedent.js");

/** Re-enable playground Edit / Binder when in-browser Java execution is ready. */
const PLAYGROUND_EDIT_ENABLED = false;
const DEFAULT_BINDER_URL =
  "https://mybinder.org/v2/gh/haifengl/smile/notebook?urlpath=lab%2Ftree%2Fshell%2Fsrc%2Funiversal%2Fnotebooks%2Findex.ipynb";

module.exports = function (config) {
  config.addGlobalData("playgroundEditEnabled", PLAYGROUND_EDIT_ENABLED);
  config.addGlobalData("defaultBinderUrl", DEFAULT_BINDER_URL);
  config.addPassthroughCopy("./src/favicon.ico");
  config.addPassthroughCopy("./src/images");
  config.addPassthroughCopy("./src/gallery");
  config.addPassthroughCopy("./src/css");
  config.addPassthroughCopy("./src/js");
  config.addPassthroughCopy("./src/fonts");

  config.addPairedShortcode("codePlayground", function (content, lang = "java", binder) {
    const safeLang = (lang || "java").replace(/[^a-z0-9+-]/gi, "") || "java";
    const binderUrl = binder || DEFAULT_BINDER_URL;
    const label = safeLang.charAt(0).toUpperCase() + safeLang.slice(1);
    const extraActions = PLAYGROUND_EDIT_ENABLED
      ? `<button type="button" class="btn btn-ghost btn-sm playground-edit">Edit</button>
    <a class="btn btn-primary btn-sm" href="${binderUrl}" target="_blank" rel="noopener">Open in Binder</a>`
      : "";
    return `<div class="code-playground glass" data-lang="${safeLang}">
  <div class="playground-toolbar">
    <span class="lang-tab is-active">${label}</span>
    <span class="toolbar-spacer"></span>
    <button type="button" class="btn btn-ghost btn-sm playground-copy">Copy</button>
    ${extraActions}
  </div>
  <pre class="playground-source language-${safeLang}"><code class="language-${safeLang}">${dedent(content)}</code></pre>
  <div class="playground-editor" hidden></div>
</div>`;
  });

  config.on("eleventy.after", async ({ dir, results }) => {
    const index = [];
    for (const page of results || []) {
      if (!page || !page.outputPath || !page.outputPath.endsWith(".html")) continue;
      const url = page.url || path.basename(page.outputPath);
      const content = page.content || "";
      const titleMatch = content.match(/<title>([^<]*)<\/title>/i);
      const title = titleMatch
        ? titleMatch[1].replace(/&amp;/g, "&").replace(/&mdash;/g, "—").trim()
        : url;
      const headings = [];
      const re = /<h([23])[^>]*id="([^"]+)"[^>]*>([\s\S]*?)<\/h\1>/gi;
      let m;
      while ((m = re.exec(content)) !== null) {
        const text = m[3].replace(/<[^>]+>/g, "").trim();
        if (text) headings.push({ id: m[2], text, level: Number(m[1]) });
      }
      index.push({ title, url: url.replace(/^\//, ""), headings });
    }
    const outDir = dir && dir.output ? dir.output : "_site";
    fs.writeFileSync(path.join(outDir, "search-index.json"), JSON.stringify(index, null, 2));
  });

  return {
    htmlTemplateEngine: "njk",
    dir: {
      input: "src",
    },
  };
};
