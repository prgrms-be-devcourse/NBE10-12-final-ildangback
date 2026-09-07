package com.gommit.domain.checkin.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.gommit.domain.checkin.support.DailyLogMontageBuilder.Frame;
import com.gommit.domain.checkin.support.DailyLogMontageBuilder.Kind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

// 로컬 ffmpeg 로 실제 그리드 mp4 가 나오는지 확인. ffmpeg 이 PATH 에 없으면(CI 등) 통째로 스킵.
@DisplayName("DailyLogMontageBuilder — ffmpeg 그리드 인코딩")
@EnabledIf("ffmpegAvailable")
class DailyLogMontageBuilderFfmpegTest {

    static boolean ffmpegAvailable() {
        try {
            return new ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start().waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static Resource png(int w, int h, String color) throws Exception {
        Path tmp = Files.createTempFile("montage-test-", ".png");
        new ProcessBuilder(
                        "ffmpeg", "-y", "-f", "lavfi", "-i",
                        "color=c=%s:s=%dx%d".formatted(color, w, h), "-frames:v", "1", tmp.toString())
                .redirectErrorStream(true)
                .start()
                .waitFor();
        Resource resource = new ByteArrayResource(Files.readAllBytes(tmp));
        Files.deleteIfExists(tmp);
        return resource;
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5, 6})
    @DisplayName("N칸 × 2회차 그리드가 1080×1920 mp4 로 인코딩된다(빈 칸 포함)")
    void gridEncodes(int cellCount) throws Exception {
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
        assertThat(new String(out.get(), 4, 4)).isEqualTo("ftyp"); // mp4 시그니처
    }
}
