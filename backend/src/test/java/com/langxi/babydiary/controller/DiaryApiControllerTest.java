package com.langxi.babydiary.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.langxi.babydiary.dto.LoginDTO;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Disabled("Requires a live database with fixed production data; covered by focused tests.")
class DiaryApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String token;
    private static Integer diaryId;

    @BeforeEach
    void setUp() throws Exception {
        if (token == null) {
            LoginDTO loginDTO = new LoginDTO();
            loginDTO.setUsername("existing-test-user");
            loginDTO.setPassword("test-password");

            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginDTO)))
                    .andReturn();

            String response = result.getResponse().getContentAsString();
            token = objectMapper.readTree(response).path("data").path("token").asText();
        }
    }

    @Test
    @Order(1)
    void testGetDiaryList() throws Exception {
        mockMvc.perform(get("/api/diaries")
                        .header("Authorization", "Bearer " + token)
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").isArray());
    }

    @Test
    @Order(2)
    void testGetDiaryListWithDateRange() throws Exception {
        mockMvc.perform(get("/api/diaries")
                        .header("Authorization", "Bearer " + token)
                        .param("startDate", "2022-01-01")
                        .param("endDate", "2025-12-31")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(3)
    void testGetDiaryListWithKeyword() throws Exception {
        mockMvc.perform(get("/api/diaries")
                        .header("Authorization", "Bearer " + token)
                        .param("keyword", "test")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @Order(4)
    void testGetDiaryListWithoutToken() throws Exception {
        mockMvc.perform(get("/api/diaries"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(5)
    void testCreateDiary() throws Exception {
        MvcResult result = mockMvc.perform(multipart("/api/diaries")
                        .file("imageFiles", new byte[0])
                        .param("title", "API Test Diary")
                        .param("content", "This is a test diary from API")
                        .param("date", "2024-01-01")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        String response = result.getResponse().getContentAsString();
        diaryId = objectMapper.readTree(response).path("data").path("diaryId").asInt();
    }

    @Test
    @Order(6)
    void testGetDiaryById() throws Exception {
        if (diaryId != null) {
            mockMvc.perform(get("/api/diaries/" + diaryId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.title").value("API Test Diary"));
        }
    }

    @Test
    @Order(7)
    void testUpdateDiary() throws Exception {
        if (diaryId != null) {
            mockMvc.perform(multipart("/api/diaries/" + diaryId)
                            .file("imageFiles", new byte[0])
                            .param("title", "Updated API Test Diary")
                            .param("content", "Updated content")
                            .param("date", "2024-01-02")
                            .header("Authorization", "Bearer " + token)
                            .with(request -> {
                                request.setMethod("PUT");
                                return request;
                            }))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    @Test
    @Order(8)
    void testDeleteDiary() throws Exception {
        if (diaryId != null) {
            mockMvc.perform(delete("/api/diaries/" + diaryId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    @Test
    @Order(9)
    void testGetNonExistentDiary() throws Exception {
        mockMvc.perform(get("/api/diaries/99999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    @Order(10)
    void testExportImages() throws Exception {
        mockMvc.perform(get("/api/diaries/export")
                        .header("Authorization", "Bearer " + token)
                        .param("startDate", "2022-01-01")
                        .param("endDate", "2025-12-31"))
                .andExpect(status().isOk());
    }
}
