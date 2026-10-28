package com.dormitory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class SmartDormitoryApplicationTest {
    @TempDir
    Path directory;

    @Test
    void findsLocalProjectFromRootOrBackendDirectory() throws IOException {
        Path backend = Files.createDirectories(directory.resolve("backend"));
        Files.createDirectories(directory.resolve("scripts"));
        Files.createDirectories(directory.resolve("miniprogram-student/utils"));
        Files.createFile(directory.resolve("scripts/configure-miniprogram.js"));
        Files.createFile(directory.resolve("miniprogram-student/utils/config.js"));

        assertEquals(directory, SmartDormitoryApplication.findLocalProject(directory));
        assertEquals(directory, SmartDormitoryApplication.findLocalProject(backend));
    }

    @Test
    void doesNotConfigureWhenLocalProjectFilesAreMissing() {
        assertNull(SmartDormitoryApplication.findLocalProject(directory));
    }

    @Test
    void startsLocalConfigurationWithActualServerPort() throws Exception {
        try {
            assumeTrue(new ProcessBuilder("node", "--version").start().waitFor() == 0);
        } catch (IOException e) {
            assumeTrue(false, "Node.js is not available");
        }
        Path backend = Files.createDirectories(directory.resolve("backend"));
        Files.createDirectories(directory.resolve("scripts"));
        Files.createDirectories(directory.resolve("miniprogram-student/utils"));
        Path result = directory.resolve("port.txt");
        Files.writeString(directory.resolve("scripts/configure-miniprogram.js"),
            "require('node:fs').writeFileSync('port.txt', process.env.SERVER_PORT);");
        Files.createFile(directory.resolve("miniprogram-student/utils/config.js"));

        SmartDormitoryApplication.configureLocalMiniProgram(backend, 8088);

        assertEquals("8088", Files.readString(result));
    }
}
