package com.gommit.domain.checkin.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.gommit.domain.checkin.support.DailyLogMontageBuilder.Frame;
import com.gommit.domain.checkin.support.DailyLogMontageBuilder.Motion;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

// 로컬 ffmpeg 로 실제 그리드 mp4 가 나오는지 확인. ffmpeg 이 PATH 에 없으면(CI 등) 통째로 스킵.
// (CheckInVideoTranscoderTest 와 같은 스킵 방식 — AbstractFfmpegTest 공유.)
@DisplayName("DailyLogMontageBuilder — ffmpeg 그리드 인코딩")
@EnabledIf("ffmpegAvailable")
class DailyLogMontageBuilderFfmpegTest extends AbstractFfmpegTest {

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

    // 영상 칸 테스트용 합성 mp4(짧은 클립 — 실제 체크인 영상처럼 회차 길이보다 짧을 수 있는 케이스도 겸함).
    private static Resource mp4(double durationSeconds, int w, int h, String color) throws Exception {
        Path tmp = Files.createTempFile("montage-test-", ".mp4");
        new ProcessBuilder(
                        "ffmpeg",
                        "-y",
                        "-f",
                        "lavfi",
                        "-i",
                        "color=c=%s:s=%dx%d:d=%s".formatted(color, w, h, durationSeconds),
                        tmp.toString())
                .redirectErrorStream(true)
                .start()
                .waitFor();
        Resource resource = new ByteArrayResource(Files.readAllBytes(tmp));
        Files.deleteIfExists(tmp);
        return resource;
    }

    @ParameterizedTest
    @CsvSource({"1, 1280, 1280", "2, 1280, 640", "3, 1278, 426", "4, 1280, 1280", "5, 1278, 852", "6, 1278, 852"})
    @DisplayName("N칸 × 2회차 그리드가 지정 해상도 mp4 로 인코딩된다(방향 섞인 입력 + 빈 칸)")
    void gridEncodesAtExpectedResolution(int cellCount, int expectedWidth, int expectedHeight) throws Exception {
        DailyLogMontageBuilder builder = new DailyLogMontageBuilder("ffmpeg", new FfmpegProcessRunner());

        String[] colors = {"red", "green", "blue", "yellow", "cyan", "magenta"};
        int[][] sizes = {{1080, 1920}, {1920, 1080}, {1000, 1000}, {800, 1200}, {1200, 800}, {900, 1600}};

        List<List<Frame>> rounds = new ArrayList<>();
        for (int r = 0; r < 2; r++) {
            List<Frame> slots = new ArrayList<>();
            for (int k = 0; k < cellCount; k++) {
                boolean blank = k == 0 && r == 1; // 2회차 첫 칸은 비움(검정) 검증
                slots.add(blank ? null : new Frame(png(sizes[k][0], sizes[k][1], colors[k]), "png", Motion.STILL));
            }
            rounds.add(slots);
        }

        Optional<byte[]> out = builder.build(cellCount, rounds);

        assertThat(out).isPresent();
        assertThat(new String(out.get(), 4, 4, StandardCharsets.US_ASCII)).isEqualTo("ftyp"); // mp4 시그니처
        assertThat(probeResolution(out.get())).isEqualTo(expectedWidth + "x" + expectedHeight);
    }

    @Test
    @DisplayName("영상 칸이 섞인 그리드도 정상 인코딩되고, 회차별 길이(ROUND_SECONDS)에 맞춰 tpad/trim 된다")
    void gridWithVideoCellsEncodesAndPadsToRoundLength() throws Exception {
        DailyLogMontageBuilder builder = new DailyLogMontageBuilder("ffmpeg", new FfmpegProcessRunner());

        // 1칸: 회차 길이(2초)보다 짧은 영상 — tpad 로 마지막 프레임이 채워지는지 확인.
        // 2칸: 정지 이미지. 3칸: 회차 2에서만 인증(회차 1은 검정).
        List<List<Frame>> rounds = List.of(
                Arrays.asList(
                        new Frame(mp4(0.5, 640, 480, "red"), "mp4", Motion.VIDEO),
                        new Frame(png(640, 480, "blue"), "png", Motion.STILL),
                        null),
                List.of(
                        new Frame(mp4(0.5, 640, 480, "green"), "mp4", Motion.VIDEO),
                        new Frame(png(640, 480, "yellow"), "png", Motion.STILL),
                        new Frame(png(640, 480, "cyan"), "png", Motion.STILL)));

        Optional<byte[]> out = builder.build(3, rounds);

        assertThat(out).isPresent();
        assertThat(new String(out.get(), 4, 4, StandardCharsets.US_ASCII)).isEqualTo("ftyp"); // mp4 시그니처
        // 회차 2개 × 회차당 2초(ROUND_SECONDS) = 4초. 영상 칸이 회차보다 짧아도(0.5초) 총 길이는 안 줄어야 한다.
        assertThat(probeDuration(out.get())).isCloseTo(4.0, org.assertj.core.data.Offset.offset(0.2));
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
