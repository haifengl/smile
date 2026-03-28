module.exports = function(config) {
  // passthrough file copy
  config.addPassthroughCopy("./src/favicon.ico");
  config.addPassthroughCopy("./src/images");
  config.addPassthroughCopy("./src/gallery");
  config.addPassthroughCopy("./src/css");
  config.addPassthroughCopy("./src/js");

  return {
    htmlTemplateEngine: "njk",
    dir: {
      input: "src"
    }
  };
};
