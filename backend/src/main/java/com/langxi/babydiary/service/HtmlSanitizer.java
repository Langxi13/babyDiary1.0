package com.langxi.babydiary.service;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Service;

@Service
public class HtmlSanitizer {
    private final Safelist safelist = Safelist.basic()
            .addTags("h1", "h2", "h3", "h4", "hr", "s", "blockquote", "pre", "code")
            .addAttributes("a", "target", "rel")
            .addProtocols("a", "href", "http", "https", "mailto");

    public String sanitize(String html) {
        if (html == null) {
            return "";
        }
        return Jsoup.clean(html, safelist);
    }
}
