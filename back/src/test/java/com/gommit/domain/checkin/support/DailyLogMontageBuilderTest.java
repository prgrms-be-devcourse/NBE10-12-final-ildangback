package com.gommit.domain.checkin.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.gommit.domain.checkin.support.DailyLogMontageBuilder.Frame;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;

@DisplayName("DailyLogMontageBuilder")
class DailyLogMontageBuilderTest {

    @Test
    @DisplayName("프레임이 없으면 서브프로세스를 띄우지 않고 즉시 빈 값을 반환한다")
    void emptyFramesReturnsEmpty() {
        DailyLogMontageBuilder builder = new DailyLogMontageBuilder("ffmpeg");

        assertThat(builder.build(List.of())).isEmpty();
    }

    @Test
    @DisplayName("ffmpeg 실행 파일이 없으면 예외 없이 빈 값으로 graceful degrade 한다")
    void missingFfmpegGracefullyDegrades() {
        DailyLogMontageBuilder builder = new DailyLogMontageBuilder("/no/such/ffmpeg-binary-xyz");
        Frame frame = new Frame(new ByteArrayResource(new byte[] {1, 2, 3}), "png");

        Optional<byte[]> result = builder.build(List.of(frame));

        assertThat(result).isEmpty();
    }
}
