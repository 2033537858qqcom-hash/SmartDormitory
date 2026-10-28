package com.dormitory;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
@MapperScan("com.dormitory.mapper")
@EnableScheduling
public class SmartDormitoryApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(SmartDormitoryApplication.class);
        application.addListeners((ApplicationReadyEvent event) -> updateMiniProgramAddress(event));
        application.run(args);
    }

    private static void updateMiniProgramAddress(ApplicationReadyEvent event) {
        if (!(event.getApplicationContext() instanceof WebServerApplicationContext webContext)) return;
        configureLocalMiniProgram(Path.of(System.getProperty("user.dir")), webContext.getWebServer().getPort());
    }

    static void configureLocalMiniProgram(Path workingDirectory, int port) {
        Path root = findLocalProject(workingDirectory);
        if (root == null) return;

        Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder("node", root.resolve("scripts/configure-miniprogram.js").toString());
            builder.directory(root.toFile()).inheritIO();
            builder.environment().put("SERVER_PORT", Integer.toString(port));
            process = builder.start();
            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                System.err.println("小程序地址自动配置超时，请手动运行 node scripts/configure-miniprogram.js");
            } else if (process.exitValue() != 0) {
                System.err.println("小程序地址自动配置失败，请检查上方提示。");
            }
        } catch (IOException e) {
            System.err.println("无法运行小程序地址配置脚本，请确认已安装 Node.js：" + e.getMessage());
        } catch (InterruptedException e) {
            if (process != null) process.destroyForcibly();
            Thread.currentThread().interrupt();
        }
    }

    static Path findLocalProject(Path workingDirectory) {
        for (Path dir = workingDirectory.toAbsolutePath().normalize(); dir != null; dir = dir.getParent()) {
            if (Files.isRegularFile(dir.resolve("scripts/configure-miniprogram.js")) &&
                Files.isRegularFile(dir.resolve("miniprogram-student/utils/config.js"))) return dir;
        }
        return null;
    }
}
