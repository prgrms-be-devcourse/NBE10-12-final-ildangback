package com.gommit.domain.checkin.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("FfmpegProcessRunner")
class FfmpegProcessRunnerTest {

    private final FfmpegProcessRunner runner = new FfmpegProcessRunner();
    private Path tempDir;

    @AfterEach
    void cleanUp() {
        runner.deleteRecursively(tempDir);
    }

    @Test
    @DisplayName("run: 프로세스 exit code 를 그대로 돌려준다")
    void runReturnsExitCode() throws Exception {
        tempDir = Files.createTempDirectory("ffmpeg-runner-test-");

        int exitCode = runner.run(tempDir, List.of("sh", "-c", "exit 3"), 5);

        assertThat(exitCode).isEqualTo(3);
    }

    @Test
    @DisplayName("run: 타임아웃 초과시 프로세스를 강제 종료하고 -1 을 돌려준다")
    void runReturnsMinusOneOnTimeout() throws Exception {
        tempDir = Files.createTempDirectory("ffmpeg-runner-test-");

        int exitCode = runner.run(tempDir, List.of("sh", "-c", "sleep 5"), 1);

        assertThat(exitCode).isEqualTo(-1);
    }

    @Test
    @DisplayName("deleteRecursively: 하위 파일·디렉터리까지 전부 지운다")
    void deleteRecursivelyRemovesNestedContents() throws IOException {
        tempDir = Files.createTempDirectory("ffmpeg-runner-test-");
        Path nested = Files.createDirectory(tempDir.resolve("nested"));
        Files.writeString(nested.resolve("a.txt"), "x");
        Files.writeString(tempDir.resolve("b.txt"), "y");

        runner.deleteRecursively(tempDir);

        assertThat(Files.exists(tempDir)).isFalse();
    }

    @Test
    @DisplayName("deleteRecursively: null 이나 존재하지 않는 경로는 조용히 무시한다")
    void deleteRecursivelyIgnoresNullOrMissing() {
        runner.deleteRecursively(null);
        runner.deleteRecursively(Path.of("/no/such/checkin-video-dir"));
        // 예외 없이 끝나면 통과
    }

    @Test
    @DisplayName("withTempWorkDir: 디렉터리를 만들어 action 에 넘기고 끝나면 삭제한다")
    void withTempWorkDirCreatesThenCleansUp() throws Exception {
        Path[] captured = new Path[1];

        String result = runner.withTempWorkDir("ffmpeg-runner-test-", workDir -> {
            captured[0] = workDir;
            assertThat(Files.exists(workDir)).isTrue();
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(Files.exists(captured[0])).isFalse();
    }

    @Test
    @DisplayName("withTempWorkDir: action 이 예외를 던져도 디렉터리는 정리되고 예외는 그대로 전파된다")
    void withTempWorkDirCleansUpEvenOnFailure() {
        Path[] captured = new Path[1];

        assertThatThrownBy(() -> runner.withTempWorkDir("ffmpeg-runner-test-", (Path workDir) -> {
                    captured[0] = workDir;
                    throw new IOException("boom");
                }))
                .isInstanceOf(IOException.class)
                .hasMessage("boom");

        assertThat(Files.exists(captured[0])).isFalse();
    }
}
