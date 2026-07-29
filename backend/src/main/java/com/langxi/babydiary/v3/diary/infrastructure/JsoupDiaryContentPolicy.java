package com.langxi.babydiary.v3.diary.infrastructure;

import com.langxi.babydiary.v3.diary.application.DiaryContentPolicy;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;

@Component
public class JsoupDiaryContentPolicy implements DiaryContentPolicy {
    private final Safelist safelist = Safelist.basic()
            .addTags("h1", "h2", "h3", "h4", "hr", "s", "blockquote", "pre", "code")
            .addAttributes("a", "target", "rel")
            .addProtocols("a", "href", "http", "https", "mailto");

    @Override
    public Content normalize(String html) {
        String sanitized = Jsoup.clean(html == null ? "" : html, safelist);
        return new Content(sanitized, Jsoup.parseBodyFragment(sanitized).text());
    }
}
