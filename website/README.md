# SMILE documentation website (Eleventy)

## Develop

```bash
npm install
npm run serve
```

## Build

```bash
npm run build   # Tailwind → site.css, then Eleventy → _site/
```

## Stack

- Eleventy 3 static site
- Tailwind CSS 4 (`src/css/input.css` → `src/css/site.css`)
- Light default + dark theme toggle (`localStorage` key `smile-theme`)
- Prism (vendored) + command palette (`search-index.json`)
- Code playgrounds with Copy / Edit (Monaco) / Open in Binder
