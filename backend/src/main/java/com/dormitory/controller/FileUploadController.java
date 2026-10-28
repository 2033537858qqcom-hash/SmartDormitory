package com.dormitory.controller;

import com.dormitory.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 演示用本地上传：文件落盘到 uploads/，通过 /uploads/** 静态访问。
 * 正式环境可替换为对象存储；路径存入业务表即可。
 */
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class FileUploadController {

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    private static boolean isImageMagic(byte[] h) {
        if (h == null || h.length < 4) return false;
        // JPEG: FF D8 FF
        if ((h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) return true;
        // PNG: 89 50 4E 47
        if ((h[0] & 0xFF) == 0x89 && h[1] == 0x50 && h[2] == 0x4E && h[3] == 0x47) return true;
        // GIF: 47 49 46 38
        if (h[0] == 0x47 && h[1] == 0x49 && h[2] == 0x46 && h[3] == 0x38) return true;
        // WEBP: RIFF
        if (h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F') return true;
        return false;
    }

    @PostMapping("/image")
    public Result<Map<String, Object>> uploadImage(@RequestParam("file") MultipartFile file,
                                                   @RequestParam(value = "biz", defaultValue = "repair") String biz) throws IOException {
        if (file == null || file.isEmpty()) {
            return Result.error("请选择图片文件");
        }
        String ct = file.getContentType() != null ? file.getContentType() : "";
        if (!ct.startsWith("image/")) {
            return Result.error("仅支持图片上传");
        }
        if (file.getSize() > 3 * 1024 * 1024L) {
            return Result.error("单张图片请控制在 3MB 以内");
        }
        byte[] magic = new byte[8];
        try (var is = file.getInputStream()) {
            int read = is.read(magic);
            if (read < 4 || !isImageMagic(magic)) {
                return Result.error("文件内容非合法图片格式（仅支持 JPG/PNG/GIF/WEBP）");
            }
        }
        String ext = ".jpg";
        String original = file.getOriginalFilename();
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.')).toLowerCase();
            if (!Set.of(".jpg", ".jpeg", ".png", ".webp", ".gif").contains(ext)) {
                ext = ".jpg";
            }
        }
        String safeBiz = biz.replaceAll("[^a-zA-Z0-9_-]", "");
        if (safeBiz.isEmpty()) safeBiz = "misc";
        Path dir = Paths.get(uploadDir, safeBiz).toAbsolutePath().normalize();
        Files.createDirectories(dir);
        String name = System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8) + ext;
        Path dest = dir.resolve(name);
        file.transferTo(dest.toFile());
        String url = "/uploads/" + safeBiz + "/" + name;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", url);
        data.put("path", url);
        data.put("size", file.getSize());
        data.put("note", "演示环境本地落盘，详见 sql/MIGRATION.md");
        return Result.success("上传成功", data);
    }
}
