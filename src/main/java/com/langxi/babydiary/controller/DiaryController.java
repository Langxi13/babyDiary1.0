package com.langxi.babydiary.controller;

import com.langxi.babydiary.entity.Diary;
import com.langxi.babydiary.entity.User;
import com.langxi.babydiary.service.DiaryService;
import com.langxi.babydiary.service.LoginService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Controller
@RequestMapping("/diary")
public class DiaryController {

    @Autowired
    private DiaryService diaryService;

    @Autowired
    private LoginService loginService;

    @Value("${diaryPageSize}")
    private int pageSize;

    // 封装获取当前登录用户 userId 的方法
    private Integer getCurrentUserId(Model model) {
        // 获取当前登录用户的用户名
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();

        // 根据用户名查询用户信息
        User user = loginService.findByUsername(username);
        if (user == null) {
            model.addAttribute("error", "User not found.");
            return null;
        }
        return user.getUserId();
    }


    @GetMapping("/form")
    public String diaryForm() {
        return "diary-form";
    }

    // 显示编辑日记页面
    @GetMapping("/edit/{diaryId}")
    public String editDiary(@PathVariable Integer diaryId, Model model) {
        Diary diary = diaryService.findDiaryById(diaryId);
        // 将 imagePaths 拆分为列表
        //判断是否为空
        if (diary.getImagePaths() != null && !diary.getImagePaths().isEmpty()) {
            List<String> imagePaths = Arrays.asList(diary.getImagePaths().split(","));
            model.addAttribute("imagePaths", imagePaths);
        }
        model.addAttribute("diary", diary);
        return "diary-edit";
    }

    // 更新日记 更新后返回详情页未开发
    @PostMapping("/update")
    public String updateDiary(@ModelAttribute Diary diary,
                              @RequestParam("imageFiles") MultipartFile[] imageFiles,
                              Model model) throws IOException {
        diaryService.updateDiary(diary, imageFiles);
        //返回详情页
        //model.addAttribute("diary", diaryService.findDiaryById(diary.getDiaryId()));
        return "redirect:/diary/list";
    }
    // 删除日记
    @GetMapping("/delete/{diaryId}")
    public String deleteDiary(@PathVariable Integer diaryId) {
        diaryService.deleteDiary(diaryId);
        return "redirect:/diary/list";
    }

    @PostMapping("/save")
    public String saveDiary(@ModelAttribute Diary diary,
                            @RequestParam("imageFiles") MultipartFile[] imageFiles,
                            Model model) throws IOException {
        // 调用封装的方法获取 userId
        Integer userId = getCurrentUserId(model);
        if (userId == null) {
            model.addAttribute("error", "User not found.");
            return "diary-form";
        }
        // 获取当前登录用户的用户名
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        // 设置日记的 userId
        diary.setUserId(userId);
        // 保存日记
        diaryService.saveDiary(diary, imageFiles);
        return "redirect:/diary/list";
    }

    @GetMapping("/list")
    public String diaryList(@RequestParam(required = false) String startDate,
                            @RequestParam(required = false) String endDate,
                            @RequestParam(defaultValue = "0") int page,
                            Model model) {
        // 获取当前登录用户的 userId
        Integer userId = getCurrentUserId(model);
        if (userId == null) {
            model.addAttribute("error", "User not found.");
            return "diary-form";
        }

        // 如果没有传递 startDate，则设置为 2022-01-01
        if (startDate == null || startDate.isEmpty()) {
            startDate = "2022-01-01";
        }

        // 如果没有传递 endDate，则设置为当前日期
        if (endDate == null || endDate.isEmpty()) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
            endDate = dateFormat.format(new Date());
        }

        // 查询日记（分页）
        Page<Diary> diaries = diaryService.getDiariesByDateRange(userId, startDate, endDate, PageRequest.of(page, pageSize));
        model.addAttribute("diaries", diaries);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);

        return "diary-list";
    }

    @GetMapping("/export")
    public ResponseEntity<FileSystemResource> exportImages (@RequestParam String startDate,
                                                            @RequestParam String endDate, Model model) throws IOException {
        // 调用封装的方法获取 userId
        Integer userId = getCurrentUserId(model);
        if (userId == null) {
            model.addAttribute("error", "User not found.");
            return null;
        }
        File zipFile = diaryService.exportImagesAsZip(userId, startDate, endDate);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=diary_images.zip")
                .body(new FileSystemResource(zipFile));
    }

}