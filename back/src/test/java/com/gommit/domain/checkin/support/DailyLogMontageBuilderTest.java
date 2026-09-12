package com.gommit.domain.checkin.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.gommit.domain.checkin.support.DailyLogMontageBuilder.Frame;
import com.gommit.domain.checkin.support.DailyLogMontageBuilder.Kind;
import com.gommit.domain.checkin.support.DailyLogMontageBuilder.Layout;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ByteArrayResource;

@DisplayName("DailyLogMontageBuilder")
class DailyLogMontageBuilderTest {

    private static Frame image() {
        return new Frame(new ByteArrayResource(new byte[] {1, 2, 3}), "png", Kind.IMAGE);
    }

    private static List<Frame> slots(Frame... frames) {
        return new ArrayList<>(Arrays.asList(frames));
    }

    @Nested
    @DisplayName("입력 검증")
    class Validation {

        @Test
        @DisplayName("회차가 없으면 서브프로세스를 띄우지 않고 즉시 빈 값을 반환한다")
        void emptyRoundsReturnsEmpty() {
            DailyLogMontageBuilder builder = new DailyLogMontageBuilder("ffmpeg");

            assertThat(builder.build(2, List.of())).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(ints = {0, 7})
        @DisplayName("칸 수가 1~6 밖이면 빈 값을 반환한다")
        void cellCountOutOfRangeReturnsEmpty(int cellCount) {
            DailyLogMontageBuilder builder = new DailyLogMontageBuilder("ffmpeg");

            assertThat(builder.build(cellCount, List.of(slots(image())))).isEmpty();
        }

        @Test
        @DisplayName("ffmpeg 실행 파일이 없으면 예외 없이 빈 값으로 graceful degrade 한다")
        void missingFfmpegGracefullyDegrades() {
            DailyLogMontageBuilder builder = new DailyLogMontageBuilder("/no/such/ffmpeg-binary-xyz");

            Optional<byte[]> result = builder.build(2, List.of(slots(image(), null)));

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("레이아웃 테이블")
    class Layouts {

        @ParameterizedTest
        @ValueSource(ints = {1, 2, 3, 4, 5, 6})
        @DisplayName("캔버스 가로는 CANVAS_WIDTH(1280) 이하, 칸은 정사각(짝수)이고 가로를 열수로 나눈 값이다")
        void squareCellsFillFixedWidth(int cellCount) {
            Layout layout = Layout.forCells(cellCount);

            // 1280 이 열수(1/2/3)로 나누어떨어지지 않는 경우(3열) 정수 나눗셈으로 살짝 작아진다 —
            // 그래도 정사각 칸이 채워지므로 실사용엔 무해(2026-09-11 완화 ②, 캔버스 2160→1280).
            assertThat(layout.cols() * layout.cellSize()).isLessThanOrEqualTo(1280);
            assertThat(layout.cellSize()).isEqualTo(1280 / layout.cols());
            assertThat(layout.canvasHeight()).isEqualTo(layout.rows() * layout.cellSize());
            assertThat(layout.cellSize() % 2).isZero();
        }

        @ParameterizedTest
        @org.junit.jupiter.params.provider.CsvSource({
            "1, 1280, 1280",
            "2, 1280, 640",
            "3, 1278, 426",
            "4, 1280, 1280",
            "5, 1278, 852",
            "6, 1278, 852"
        })
        @DisplayName("N칸별 캔버스 해상도")
        void canvasSizePerCellCount(int cellCount, int width, int height) {
            Layout layout = Layout.forCells(cellCount);

            assertThat(layout.cols() * layout.cellSize()).isEqualTo(width);
            assertThat(layout.canvasHeight()).isEqualTo(height);
        }

        @Test
        @DisplayName("5는 6칸 레이아웃을 쓴다(마지막 칸은 검정)")
        void fiveUsesSixCellGrid() {
            assertThat(Layout.forCells(5).gridCells()).isEqualTo(6);
            assertThat(Layout.forCells(5)).isEqualTo(Layout.forCells(6));
        }

        @Test
        @DisplayName("2·3은 가로 한 줄, 4는 2×2, 5·6은 3열 2행")
        void gridShapes() {
            assertThat(Layout.forCells(2).cols()).isEqualTo(2);
            assertThat(Layout.forCells(2).rows()).isEqualTo(1);
            assertThat(Layout.forCells(3).cols()).isEqualTo(3);
            assertThat(Layout.forCells(3).rows()).isEqualTo(1);
            assertThat(Layout.forCells(4).cols()).isEqualTo(2);
            assertThat(Layout.forCells(4).rows()).isEqualTo(2);
            assertThat(Layout.forCells(6).cols()).isEqualTo(3);
            assertThat(Layout.forCells(6).rows()).isEqualTo(2);
        }

        @Test
        @DisplayName("7칸 이상은 지원하지 않는다")
        void sevenCellsUnsupported() {
            assertThatThrownBy(() -> Layout.forCells(7)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
