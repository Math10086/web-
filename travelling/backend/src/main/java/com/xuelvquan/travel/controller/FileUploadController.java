package com.xuelvquan.travel.controller;

import com.xuelvquan.travel.common.Result;
import com.xuelvquan.travel.security.AuthUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 通用文件上传接口，图片保存至static/images/footprints/ 目录
 */
@Slf4j
@RestController
@RequestMapping("/api/upload")
public class FileUploadController {

    /** 允许的图片扩展名 */
    private static final String[] ALLOWED = {"jpg", "jpeg", "png", "gif", "webp"};
    /** 单文件最大5 MB */
    private static final long MAX_SIZE = 5 * 1024 * 1024;

    @PostMapping
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        AuthUtil.requireUserId();

        if (file == null || file.isEmpty()) {
            return Result.fail(400, "请选择要上传的文件");
        }
        if (file.getSize() > MAX_SIZE) {
            return Result.fail(400, "图片大小不能超过5MB");
        }

        String original = file.getOriginalFilename();
        String ext = getExt(original);
        if (!isAllowed(ext)) {
            return Result.fail(400, "仅支持 jpg、jpeg、png、gif、webp 格式的图片");
        }

        // 存到backend/src/main/resources/static/images/footprints/
        Path dir = resolveUploadDir();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            log.error("创建上传目录失败: {}", dir, e);
            return Result.fail(500, "服务器创建目录失败");
        }

        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path target = dir.resolve(filename);
        try {
            file.transferTo(target.toFile());
        } catch (IOException e) {
            log.error("保存上传文件失败: {}", target, e);
            return Result.fail(500, "上传失败，请重试");
        }

        // 返回前端可直接使用的绝对路径
        return Result.ok("/images/footprints/" + filename);
    }

    private Path resolveUploadDir() {
        Path cwd = Paths.get(System.getProperty("user.dir")).normalize();
        // 运行时classpath目录：上传后立即可通过/images/** 访问
        Path runtime = cwd.resolve("target/classes/static/images/footprints").normalize();
        if (runtime.getParent().toFile().isDirectory()) {
            return runtime;
        }
        // 开发时fallback到源码目录
        Path source = cwd.resolve("src/main/resources/static/images/footprints").normalize();
        if (source.getParent().toFile().isDirectory()) {
            return source;
        }
        // 兜底：临时目录（生产环境应换成对象存储）
        return Paths.get(System.getProperty("java.io.tmpdir"), "xlvq-uploads").normalize();
    }

    private String getExt(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private boolean isAllowed(String ext) {
        for (String a : ALLOWED) {
            if (a.equals(ext)) {
                return true;
            }
        }
        return false;
    }
}
