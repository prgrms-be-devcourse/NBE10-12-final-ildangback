package com.gommit.domain.checkin.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.gommit.domain.checkin.support.DailyLogMontageBuilder.Frame;
import com.gommit.domain.checkin.support.DailyLogMontageBuilder.Kind;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

// 로컬 ffmpeg 로 실제 그리드 mp4 가 나오는지 확인. ffmpeg 이 PATH 에 없으면(CI 등) 통째로 스킵.
@DisplayName("DailyLogMontageBuilder — ffmpeg 그리드 인코딩")
@EnabledIf("ffmpegAvailable")
class DailyLogMontageBuilderFfmpegTest {

    static boolean ffmpegAvailable() {
        try {
            return new ProcessBuilder("ffmpeg", "-version")
                            .redirectErrorStream(true)
                            .start()
                            .waitFor()
                    == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static Resource png(int w, int h, String color) throws Exception {
        Path tmp = Files.createTempFile("montage-test-", ".png");
        new ProcessBuilder(
                        "ffmpeg",
                        "-y",
                        "-f",
                        "lavfi",
                        "-i",
                        "color=c=%s:s=%dx%d".formatted(color, w, h),
                        "-frames:v",
                        "1",
                        tmp.toString())
                .redirectErrorStream(true)
                .start()
                .waitFor();
        Resource resource = new ByteArrayResource(Files.readAllBytes(tmp));
        Files.deleteIfExists(tmp);
        return resource;
    }

    @ParameterizedTest
    @CsvSource({"1, 2160, 2160", "2, 2160, 1080", "3, 2160, 720", "4, 2160, 2160", "5, 2160, 1440", "6, 2160, 1440"})
    @DisplayName("N칸 × 2회차 그리드가 지정 해상도 mp4 로 인코딩된다(방향 섞인 입력 + 빈 칸)")
    void gridEncodesAtExpectedResolution(int cellCount, int expectedWidth, int expectedHeight) throws Exception {
        DailyLogMontageBuilder builder = new DailyLogMontageBuilder("ffmpeg");

        String[] colors = {"red", "green", "blue", "yellow", "cyan", "magenta"};
        int[][] sizes = {{1080, 1920}, {1920, 1080}, {1000, 1000}, {800, 1200}, {1200, 800}, {900, 1600}};

        List<List<Frame>> rounds = new ArrayList<>();
        for (int r = 0; r < 2; r++) {
            List<Frame> slots = new ArrayList<>();
            for (int k = 0; k < cellCount; k++) {
                boolean blank = k == 0 && r == 1; // 2회차 첫 칸은 비움(검정) 검증
                slots.add(blank ? null : new Frame(png(sizes[k][0], sizes[k][1], colors[k]), "png", Kind.IMAGE));
            }
            rounds.add(slots);
        }

        Optional<byte[]> out = builder.build(cellCount, rounds);

        assertThat(out).isPresent();
        assertThat(new String(out.get(), 4, 4, StandardCharsets.US_ASCII)).isEqualTo("ftyp"); // mp4 시그니처
        assertThat(probeResolution(out.get())).isEqualTo(expectedWidth + "x" + expectedHeight);
    }

    // 인코딩된 mp4 바이트를 임시 파일로 떨궈 ffprobe 로 해상도(WxH)를 읽는다.
    private static String probeResolution(byte[] mp4) throws Exception {
        Path tmp = Files.createTempFile("montage-probe-", ".mp4");
        try {
            Files.write(tmp, mp4);
            Process p = new ProcessBuilder(
                            "ffprobe",
                            "-v",
                            "error",
                            "-select_streams",
                            "v:0",
                            "-show_entries",
                            "stream=width,height",
                            "-of",
                            "csv=s=x:p=0",
                            tmp.toString())
                    .redirectErrorStream(true)
                    .start();
            String line;
            try (BufferedReader r =
                    new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                line = r.readLine();
            }
            p.waitFor();
            return line == null ? "" : line.trim();
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
