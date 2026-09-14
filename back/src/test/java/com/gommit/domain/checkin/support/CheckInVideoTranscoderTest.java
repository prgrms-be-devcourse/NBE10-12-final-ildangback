package com.gommit.domain.checkin.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.gommit.domain.checkin.support.CheckInVideoTranscoder.Transcoded;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;

// 로컬 ffmpeg 로 실제 트랜스코딩 결과를 확인. ffmpeg 이 PATH 에 없으면(CI 등) 통째로 스킵.
// (DailyLogMontageBuilderFfmpegTest 와 같은 스킵 방식 — AbstractFfmpegTest 공유.)
@DisplayName("CheckInVideoTranscoder — ffmpeg 트랜스코딩")
@EnabledIf("ffmpegAvailable")
class CheckInVideoTranscoderTest extends AbstractFfmpegTest {

    private final CheckInVideoTranscoder transcoder = new CheckInVideoTranscoder("ffmpeg", new FfmpegProcessRunner());

    @ParameterizedTest
    @ValueSource(doubles = {0.3, 2.0, 3.5})
    @DisplayName("원본 길이와 무관하게 결과 영상은 항상 정확히 2초다(짧으면 마지막 프레임 패딩, 길면 트림)")
    void transcodeAlwaysProducesTwoSecondClip(double sourceDurationSeconds) throws Exception {
        MockMultipartFile upload = syntheticVideo(sourceDurationSeconds);

        Transcoded result = transcoder.transcode(upload, "mp4");

        assertThat(result.video()).isNotEmpty();
        assertThat(probeDuration(result.video())).isEqualTo(2.0, org.assertj.core.data.Offset.offset(0.1));
    }

    @Test
    @DisplayName("포스터는 비어있지 않은 jpeg 로 생성된다")
    void transcodeProducesNonEmptyPoster() throws Exception {
        MockMultipartFile upload = syntheticVideo(2.0);

        Transcoded result = transcoder.transcode(upload, "mp4");

        assertThat(result.poster()).isNotEmpty();
        assertThat(new String(result.poster(), 0, 2, StandardCharsets.ISO_8859_1))
                .isEqualTo("ÿØ"); // JPEG 시그니처(FF D8)
    }

    // lavfi 로 합성 영상을 만들어 업로드 파일처럼 감싼다.
    private static MockMultipartFile syntheticVideo(double durationSeconds) throws Exception {
        Path tmp = Files.createTempFile("checkin-video-src-", ".mp4");
        try {
            new ProcessBuilder(
                            "ffmpeg",
                            "-y",
                            "-f",
                            "lavfi",
                            "-i",
                            "testsrc=size=320x240:rate=30:duration=%s".formatted(durationSeconds),
                            tmp.toString())
                    .redirectErrorStream(true)
                    .start()
                    .waitFor();
            byte[] bytes = Files.readAllBytes(tmp);
            return new MockMultipartFile("media", "clip.mp4", "video/mp4", bytes);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
