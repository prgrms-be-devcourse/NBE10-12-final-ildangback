package com.gommit.domain.checkin.support;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

// DailyLog 몽타주(그 날 인증 사진을 이어붙인 무음 슬라이드쇼, 전환 없음) 빌더. 앱 서버에서 ffmpeg 서브프로세스를 호출한다.
// ffmpeg 부재/타임아웃/실패 시 예외를 던지지 않고 Optional.empty() 로 graceful degrade — 호출부가 videoKey null 을 유지한다.
@Slf4j
@Component
public class DailyLogMontageBuilder {

    private static final int SLIDE_SECONDS = 3; // 사진 1장당 노출 시간
    private static final long TIMEOUT_SECONDS = 120;

    private final String ffmpegPath;

    public DailyLogMontageBuilder(@Value("${app.dailylog.ffmpeg-path:ffmpeg}") String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }

    // 몽타주에 쓸 프레임 한 장. extension 은 ffmpeg 이 디코더를 고르는 데 쓰인다(원본 확장자를 그대로 넘겨야 한다).
    public record Frame(Resource resource, String extension) {}

    public Optional<byte[]> build(List<Frame> frames) {
        if (frames.isEmpty()) {
            return Optional.empty();
        }

        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("dailylog-montage-");
            List<Path> files = writeFrames(workDir, frames);
            Path list = writeConcatList(workDir, files);
            Path output = workDir.resolve("out.mp4");

            int exitCode = runFfmpeg(list, output);
            if (exitCode != 0 || !Files.exists(output)) {
                log.warn("ffmpeg 몽타주 생성 실패 (exitCode={})", exitCode);
                return Optional.empty();
            }
            return Optional.of(Files.readAllBytes(output));
        } catch (IOException e) {
            log.warn("ffmpeg 실행 불가 — 몽타주 생성 건너뜀 (배포 이미지에 ffmpeg 없을 수 있음)", e);
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } finally {
            deleteRecursively(workDir);
        }
    }

    private List<Path> writeFrames(Path workDir, List<Frame> frames) throws IOException {
        List<Path> files = new ArrayList<>();
        int idx = 0;
        for (Frame frame : frames) {
            Path file = workDir.resolve("frame_%03d.%s".formatted(idx++, frame.extension()));
            try (InputStream in = frame.resource().getInputStream()) {
                Files.copy(in, file);
            }
            files.add(file);
        }
        return files;
    }

    // ffmpeg concat 데뮤서 입력 목록. 마지막 파일은 duration 없이 한 번 더 반복해야
    // 그 파일의 duration 이 온전히 반영된다(ffmpeg concat 데뮤서의 알려진 동작).
    private Path writeConcatList(Path workDir, List<Path> files) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (Path file : files) {
            sb.append("file '").append(file.getFileName()).append("'\n");
            sb.append("duration ").append(SLIDE_SECONDS).append('\n');
        }
        sb.append("file '").append(files.get(files.size() - 1).getFileName()).append("'\n");

        Path list = workDir.resolve("list.txt");
        Files.writeString(list, sb.toString());
        return list;
    }

    private int runFfmpeg(Path list, Path output) throws IOException, InterruptedException {
        List<String> command = List.of(
                ffmpegPath,
                "-y",
                "-f",
                "concat",
                "-safe",
                "0",
                "-i",
                list.toString(),
                "-vsync",
                "vfr",
                "-pix_fmt",
                "yuv420p",
                output.toString());

        Process process = new ProcessBuilder(command)
                .directory(list.getParent().toFile())
                .redirectErrorStream(true)
                .start();

        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            log.warn("ffmpeg 타임아웃 ({}초)", TIMEOUT_SECONDS);
            return -1;
        }
        return process.exitValue();
    }

    private void deleteRecursively(Path dir) {
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
}
