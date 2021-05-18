module.exports = function(eleventyConfig) {
  // passthrough file copy
  eleventyConfig.addPassthroughCopy("favicon.ico");
  eleventyConfig.addPassthroughCopy("images");
  eleventyConfig.addPassthroughCopy("gallery");
  eleventyConfig.addPassthroughCopy("css");
  eleventyConfig.addPassthroughCopy("js");

  return {
    htmlTemplateEngine: "njk"
  };
};
