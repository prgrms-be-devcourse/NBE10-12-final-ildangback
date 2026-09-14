package com.gommit.domain.checkin.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FfmpegProcessRunner {

    // 로그 소비처 없어 출력 DISCARD
    public int run(Path workDir, List<String> command, long timeoutSeconds) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .directory(workDir.toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            log.warn("ffmpeg 타임아웃 ({}초)", timeoutSeconds);
            return -1;
        }
        return process.exitValue();
    }

    public void deleteRecursively(Path dir) {
        if (dir == null) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // best-effort 정리
                }
            });
        } catch (IOException ignored) {
            // best-effort 정리
        }
    }

    // tpad(마지막 프레임 정지 복제) + trim 으로 영상을 정확히 seconds 길이에 맞추는 필터 조각(선행 콤마 없음).
    // 원본이 이미 seconds 이상이면(호출부가 그 전에 "-t" 등으로 미리 잘라뒀다는 전제) trim 이 그대로 잘라
    // no-op 이 된다. CheckInVideoTranscoder.transcodeCommand, DailyLogMontageBuilder.buildRoundFilter 공용.
    public static String padAndTrimFilter(int seconds) {
        return "tpad=stop_mode=clone:stop_duration=%1$d,trim=duration=%1$d,setpts=PTS-STARTPTS".formatted(seconds);
    }

    @FunctionalInterface
    public interface TempWorkAction<T> {
        T run(Path workDir) throws IOException, InterruptedException;
    }

    // 임시 작업 디렉터리 생성 → action 실행 → 성공/실패 무관하게 재귀 삭제.
    // 예외 처리는 호출부마다 다르므로(예외를 던지거나 삼키고 로그만 남기거나) 여기선 그대로 던지고
    // catch 는 호출부 책임으로 남긴다.
    public <T> T withTempWorkDir(String prefix, TempWorkAction<T> action) throws IOException, InterruptedException {
        Path workDir = Files.createTempDirectory(prefix);
        try {
            return action.run(workDir);
        } finally {
            deleteRecursively(workDir);
        }
    }
}
