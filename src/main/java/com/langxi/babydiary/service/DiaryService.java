package com.langxi.babydiary.service;

import com.langxi.babydiary.mapper.DiaryMapper;
import com.langxi.babydiary.entity.Diary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class DiaryService {

    @Autowired
    private DiaryMapper diaryMapper;


    @Value("${diaryFilePath}") // 从配置文件中读取本地文件存储路径
    private String uploadDir;

    // 检查文件是否为图片
    private boolean isImageFile(MultipartFile file) {
        String contentType = file.getContentType();
        return contentType != null && contentType.startsWith("image");
    }

    //通过ID查询日记
    public Diary findDiaryById(Integer diaryId) {
        return diaryMapper.findDiaryById(diaryId);
    }

    public void saveDiary(Diary diary,  MultipartFile[] imageFiles) throws IOException {
        // 处理图片上传
        if (imageFiles != null && imageFiles.length > 0) {
            //用户名
            String username = diaryMapper.findUsernameByUserId(diary.getUserId());
            List<String> imagePaths = new ArrayList<>();
            for (MultipartFile imageFile : imageFiles) {
                if (!imageFile.isEmpty() && isImageFile(imageFile)) {
                    // 生成唯一的文件名
                    String fileName = username + "_" + System.currentTimeMillis() + "_" + imageFile.getOriginalFilename();
                    // 保存图片到本地文件系统
                    Path path = Paths.get(uploadDir + fileName);
                    Files.write(path, imageFile.getBytes());
                    imagePaths.add(fileName);
                }
            }
            // 将图片路径以逗号分隔存储
            diary.setImagePaths(String.join(",", imagePaths));
        }
        // 保存日记信息到数据库
        diaryMapper.insertDiary(diary);
    }

    // 更新日记
    public void updateDiary(Diary diary, MultipartFile[] imageFiles) throws IOException {
        // 处理图片上传
        if (imageFiles != null && imageFiles.length > 0) {
            //用户名
            String username = diaryMapper.findUsernameByDiaryId(diary.getDiaryId());
            System.out.println(diary.getDiaryId());
            List<String> imagePaths = new ArrayList<>();
            for (MultipartFile imageFile : imageFiles) {
                if (!imageFile.isEmpty() && isImageFile(imageFile)) {
                    // 生成唯一的文件名
                    String fileName = username + "_" + System.currentTimeMillis() + "_" + imageFile.getOriginalFilename();
                    System.out.println(fileName);
                    // 保存图片到本地文件系统
                    Path path = Paths.get(uploadDir + fileName);
                    Files.write(path, imageFile.getBytes());
                    imagePaths.add(fileName);
                }
            }
            // 将图片路径以逗号分隔存储
            diary.setImagePaths(String.join(",", imagePaths));
        }
        if (imageFiles == null || imageFiles.length == 0)
            diary.setImagePaths(null);
            // 更新日记到数据库
        diaryMapper.updateDiary(diary);
    }

    //删除日记
    public void deleteDiary(Integer diaryId) {
        // 删除数据库中的日记记录
        diaryMapper.deleteDiary(diaryId);
    }
    public Page<Diary> getDiariesByDateRange(Integer userId, String startDate, String endDate, Pageable pageable) {
        // 查询总记录数
        int total = diaryMapper.countDiariesByDateRange(userId, startDate, endDate);
        // 查询当前页的数据
        List<Diary> diaries = diaryMapper.findDiariesPageByDateRange(userId, startDate, endDate, pageable.getPageSize(), pageable.getOffset());
        // 返回分页结果
        return new PageImpl<>(diaries, pageable, total);
    }
    public List<Diary> getDiariesByDateRange(Integer userId, String startDate, String endDate) {
        return diaryMapper.findDiariesByDateRange(userId, startDate, endDate);
    }

//    public File exportImagesAsZip(Integer userId, String startDate, String endDate) throws IOException {
//        List<String> imagePaths = diaryMapper.findImagePathsByDateRange(userId, startDate, endDate);
//        if (imagePaths.isEmpty()) {
//            return null;
//        }
//        File zipFile = File.createTempFile("diary_images", ".zip");
//        try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(zipFile.toPath()))) {
//            for (String imagePath : imagePaths) {
//                File file = new File(uploadDir + imagePath);
//                //如果文件不存在则跳过
//                if (!file.exists()) {
//                    continue;
//                }
//                zipOut.putNextEntry(new ZipEntry(file.getName()));
//                Files.copy(file.toPath(), zipOut);
//                zipOut.closeEntry();
//            }
//        }
//        return zipFile;
//    }

    //修改exportImagesAsZip方法，每个imagePaths中可能有多个图片，所以需要循环处理每个imagePaths中的图片，并添加到zip文件中。
    public File exportImagesAsZip(Integer userId, String startDate, String endDate) throws IOException {
        List<String> imagePaths = diaryMapper.findImagePathsByDateRange(userId, startDate, endDate);
        if (imagePaths.isEmpty()) {
            return null;
        }
        File zipFile = File.createTempFile("diary_images", ".zip");
        try (ZipOutputStream zipOut = new ZipOutputStream(Files.newOutputStream(zipFile.toPath()))) {
            for (String imagePath : imagePaths) {
                //判断是否有逗号或者是否为空
                if (imagePath == null || imagePath.isEmpty()) {
                    continue;
                }
                //如果有逗号分隔
                if (imagePath.contains(",")) {
                    String[] imagePathArray = imagePath.split(",");
                    for (String imagePathItem : imagePathArray) {
                        File file = new File(uploadDir + imagePathItem);
                        //如果文件不存在则跳过
                        if (!file.exists()) {
                            continue;
                        }
                        zipOut.putNextEntry(new ZipEntry(file.getName()));
                        Files.copy(file.toPath(), zipOut);
                        zipOut.closeEntry();
                    }
                }
                else {
                    File file = new File(uploadDir + imagePath);
                    //如果文件不存在则跳过
                    if (!file.exists()) {
                        continue;
                    }
                    zipOut.putNextEntry(new ZipEntry(file.getName()));
                    Files.copy(file.toPath(), zipOut);
                    zipOut.closeEntry();
                }

            }
        }
        return zipFile;
    }
}