package com.langxi.babydiary.service;

import com.langxi.babydiary.dto.AiReportGenerateDTO;
import com.langxi.babydiary.entity.AiConfig;
import com.langxi.babydiary.entity.AiReport;
import com.langxi.babydiary.entity.Diary;
import com.langxi.babydiary.mapper.AiReportMapper;
import com.langxi.babydiary.mapper.DiaryMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Date;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiReportServiceTest {

    @Mock
    private AiConfigService aiConfigService;

    @Mock
    private AiPeriodResolver aiPeriodResolver;

    @Mock
    private OpenAiCompatibleClient aiClient;

    @Mock
    private DiaryMapper diaryMapper;

    @Mock
    private TagService tagService;

    @Mock
    private AiReportMapper aiReportMapper;

    @InjectMocks
    private AiReportService aiReportService;

    @Test
    void generateReportUsesPeriodDiariesAndSavesMarkdown() {
        AiConfig config = new AiConfig();
        config.setEnabled(true);
        config.setBaseUrl("https://api.example.com/v1");
        config.setModel("test-model");
        config.setEncryptedApiKey("encrypted");

        Diary diary = new Diary();
        diary.setDiaryId(10);
        diary.setDate(Date.valueOf("2026-07-01"));
        diary.setTitle("公园散步");
        diary.setContent("<p>今天一起去了<strong>公园</strong>。</p>");
        diary.setMoodKey("happy");

        when(aiConfigService.getRuntimeConfig()).thenReturn(new AiRuntimeConfig("https://api.example.com/v1", "sk-test", "test-model", 30));
        when(aiPeriodResolver.resolve("WEEKLY", "2026-W27"))
                .thenReturn(new AiReportPeriod("WEEKLY", "2026-W27", Date.valueOf("2026-06-29"), Date.valueOf("2026-07-05")));
        when(diaryMapper.findDiariesForReport(3, Date.valueOf("2026-06-29"), Date.valueOf("2026-07-05")))
                .thenReturn(Collections.singletonList(diary));
        when(tagService.findTagsByDiaryIds(Collections.singletonList(10))).thenReturn(Collections.emptyMap());
        when(aiClient.generate(any(), any())).thenReturn("# 这一周\\n很温暖。");

        AiReportGenerateDTO dto = new AiReportGenerateDTO();
        dto.setType("WEEKLY");
        dto.setPeriod("2026-W27");

        aiReportService.generate(3, dto);

        ArgumentCaptor<AiReport> captor = ArgumentCaptor.forClass(AiReport.class);
        org.mockito.Mockito.verify(aiReportMapper).insert(captor.capture());
        AiReport saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(3);
        assertThat(saved.getType()).isEqualTo("WEEKLY");
        assertThat(saved.getPeriod()).isEqualTo("2026-W27");
        assertThat(saved.getContentMarkdown()).contains("这一周");

        ArgumentCaptor<List<AiChatMessage>> messagesCaptor = ArgumentCaptor.forClass(List.class);
        org.mockito.Mockito.verify(aiClient).generate(any(), messagesCaptor.capture());
        String systemPrompt = messagesCaptor.getValue().get(0).getContent();
        String userPrompt = messagesCaptor.getValue().get(1).getContent();
        assertThat(systemPrompt).contains("旁观整理者", "第二人称", "你/你们");
        assertThat(userPrompt).contains("在这个月中，你们一起走过了", "你完成了");
        assertThat(userPrompt).contains("不要使用“我”或“我们”代入日记主人");
        assertThat(userPrompt).contains("今天一起去了公园。");
        assertThat(userPrompt).doesNotContain("<p>", "<strong>");
    }

    @Test
    void reportListCapsPageSizeToProtectDatabaseQueries() {
        when(aiReportMapper.count(3, null)).thenReturn(0);
        when(aiReportMapper.findPage(3, null, 100, 0L)).thenReturn(Collections.emptyList());

        com.langxi.babydiary.common.PageResult<AiReport> result = aiReportService.findReports(3, null, -1, 10_000);

        assertThat(result.getPageNumber()).isZero();
        assertThat(result.getPageSize()).isEqualTo(100);
    }
}
