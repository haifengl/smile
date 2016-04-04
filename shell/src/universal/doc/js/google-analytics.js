
(function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
        (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
    m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
})(window,document,'script','http://www.google-analytics.com/analytics.js','ga');

ga('create', 'UA-52181830-5', 'auto');
ga('send', 'pageview');

(function ($) {

    // Log all jQuery AJAX requests to Google Analytics
    $(document).ajaxSend(function(event, xhr, settings){
        if (typeof _gaq !== "undefined" && _gaq !== null) {
            _gaq.push(['_trackPageview', settings.url]);
        }
    });

})(jQuery);