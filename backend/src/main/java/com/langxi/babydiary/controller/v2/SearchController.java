package com.langxi.babydiary.controller.v2;

import com.langxi.babydiary.common.Result;
import com.langxi.babydiary.dto.SearchResponseVO;
import com.langxi.babydiary.security.CurrentUser;
import com.langxi.babydiary.service.SearchService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v2/spaces/{spaceId}/search")
public class SearchController {
    private final SearchService searchService;
    private final CurrentUser currentUser;

    public SearchController(SearchService searchService, CurrentUser currentUser) {
        this.searchService = searchService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public Result<SearchResponseVO> search(@PathVariable String spaceId,
                                           @RequestParam String query,
                                           @RequestParam(defaultValue = "30") int limit) {
        return Result.success(searchService.search(spaceId, currentUser.getUserId(), query, limit));
    }
}
