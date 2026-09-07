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

// ffmpeg 부재/타임아웃/실패 시 예외를 삼키고 graceful degrade — 호출부가 videoKey null 을 유지한다.
//
// 그리드 몽타주. 하루의 인증을 회차(round)별로 묶고, 회차 1개 = 그리드 1프레임(노출 ROUND_SECONDS 초)으로
// 순차 concat 한다. 칸(cell)은 멤버 고정 슬롯이라 회차 내내 N칸 고정 — 인증 안 한 멤버의 칸은 검정.
// 고정 캔버스 CANVAS_WIDTH×CANVAS_HEIGHT(9:16 세로), 모든 칸 치수는 짝수라 yuv420p 안전.
// 인코딩 비용: 칸이 거의 정지 프레임이라 낮다. 낮은 FPS + veryfast preset 으로 EC2 크레딧 소모를 억제한다.
@Slf4j
@Component
public class DailyLogMontageBuilder {

    // 그리드 최대 칸 수 — 챌린지 인원 상한(1~6)과 같다.
    public static final int MAX_CELLS = 6;

    private static final int ROUND_SECONDS = 2; // 회차 1개 노출 시간
    private static final int CANVAS_WIDTH = 1080; // 9:16 세로 — 모바일 풀스크린
    private static final int CANVAS_HEIGHT = 1920;
    private static final int FPS = 10; // 정지 프레임이라 낮게 — 파일 크기/인코딩 비용 절감
    private static final String PRESET = "veryfast";
    private static final long TIMEOUT_SECONDS = 120;

    private final String ffmpegPath;

    public DailyLogMontageBuilder(@Value("${app.dailylog.ffmpeg-path:ffmpeg}") String ffmpegPath) {
        this.ffmpegPath = ffmpegPath;
    }

    public enum Kind {
        IMAGE,
        VIDEO
    }

    // 그리드 한 칸에 들어갈 미디어. extension 은 ffmpeg 이 디코더를 고르는 데 쓰인다(원본 확장자 그대로 넘긴다).
    public record Frame(Resource resource, String extension, Kind kind) {}

    // 캔버스를 N칸으로 나누는 레이아웃. 합계는 항상 CANVAS_WIDTH×CANVAS_HEIGHT.
    // N=5 는 6칸 레이아웃을 쓰고 마지막 1칸은 항상 검정(gridCells > cellCount).
    record Layout(int cols, int rows, int cellWidth, int cellHeight) {

        int gridCells() {
            return cols * rows;
        }

        static Layout forCells(int cellCount) {
            return switch (cellCount) {
                case 1 -> new Layout(1, 1, 1080, 1920);
                case 2 -> new Layout(2, 1, 540, 1920); // 가로 2분할
                case 3 -> new Layout(3, 1, 360, 1920); // 가로 3분할
                case 4 -> new Layout(2, 2, 540, 960); // 2×2
                case 5, 6 -> new Layout(2, 3, 540, 640); // 2열 3행 (5는 마지막 칸 검정)
                default -> throw new IllegalArgumentException("지원하지 않는 그리드 칸 수: " + cellCount);
            };
        }
    }

    // cellCount: 그리드 칸 수(1~MAX_CELLS). rounds: 회차별 슬롯 배열 — 각 내부 리스트 size == cellCount,
    // null 엔트리 = 그 회차에 그 칸 멤버가 인증하지 않음(검정으로 렌더). rounds 오름차순 = 회차 오름차순.
    public Optional<byte[]> build(int cellCount, List<List<Frame>> rounds) {
        if (cellCount < 1 || cellCount > MAX_CELLS || rounds.isEmpty()) {
            return Optional.empty();
        }
        Layout layout = Layout.forCells(cellCount);
        List<Frame> flat = flatten(cellCount, layout, rounds); // (회차 × gridCells) 순서, null = 검정 칸

        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("dailylog-montage-");
            List<String> inputNames = writeFrames(workDir, flat);
            Path output = workDir.resolve("out.mp4");

            int exitCode = runFfmpeg(workDir, layout, rounds.size(), flat, inputNames, output);
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

    // 회차별 슬롯(size == cellCount)을 gridCells 길이로 펼친다. cellCount..gridCells-1 칸은 항상 검정(null).
    private static List<Frame> flatten(int cellCount, Layout layout, List<List<Frame>> rounds) {
        List<Frame> flat = new ArrayList<>();
        for (List<Frame> slots : rounds) {
            for (int cell = 0; cell < layout.gridCells(); cell++) {
                flat.add(cell < cellCount ? slots.get(cell) : null);
            }
        }
        return flat;
    }

    // 실제 프레임만 임시 파일로 쓴다. 반환 리스트의 인덱스는 flat 과 1:1 — 검정 칸은 null.
    private List<String> writeFrames(Path workDir, List<Frame> flat) throws IOException {
        List<String> names = new ArrayList<>();
        int idx = 0;
        for (Frame frame : flat) {
            if (frame == null) {
                names.add(null);
                idx++;
                continue;
            }
            String name = "frame_%04d.%s".formatted(idx++, frame.extension());
            try (InputStream in = frame.resource().getInputStream()) {
                Files.copy(in, workDir.resolve(name));
            }
            names.add(name);
        }
        return names;
    }

    private int runFfmpeg(
            Path workDir,
            Layout layout,
            int roundCount,
            List<Frame> flat,
            List<String> inputNames,
            Path output)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");

        for (int i = 0; i < flat.size(); i++) {
            Frame frame = flat.get(i);
            if (frame == null) {
                // 검정 칸: lavfi 로 인라인 생성(임시 파일/추가 프로세스 불필요).
                command.add("-f");
                command.add("lavfi");
                command.add("-t");
                command.add(String.valueOf(ROUND_SECONDS));
                command.add("-i");
                command.add("color=c=black:s=%dx%d:r=%d".formatted(layout.cellWidth(), layout.cellHeight(), FPS));
            } else if (frame.kind() == Kind.VIDEO) {
                // TODO(동영상 인증): 앞 ROUND_SECONDS 초만 사용. 클립이 더 짧으면 filter 의 tpad 로 마지막 프레임을 채운다.
                command.add("-t");
                command.add(String.valueOf(ROUND_SECONDS));
                command.add("-i");
                command.add(inputNames.get(i));
            } else {
                command.add("-loop");
                command.add("1");
                command.add("-t");
                command.add(String.valueOf(ROUND_SECONDS));
                command.add("-i");
                command.add(inputNames.get(i));
            }
        }

        command.add("-filter_complex");
        command.add(buildFilter(layout, roundCount));
        command.add("-map");
        command.add("[out]");
        command.add("-r");
        command.add(String.valueOf(FPS));
        command.add("-pix_fmt");
        command.add("yuv420p");
        command.add("-preset");
        command.add(PRESET);
        command.add("-movflags");
        command.add("+faststart");
        command.add(output.getFileName().toString());

        Process process = new ProcessBuilder(command)
                .directory(workDir.toFile())
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

    // 칸별 정규화(scale+pad+fps) → 행마다 hstack → 행들 vstack → 회차별 [round_r] → concat.
    private static String buildFilter(Layout layout, int roundCount) {
        int grid = layout.gridCells();
        StringBuilder sb = new StringBuilder();

        // 모든 입력을 정확한 셀 크기로 정규화. 사진 전체가 보이게 축소(decrease) 후 칸 안쪽만 검정 pad.
        for (int i = 0; i < roundCount * grid; i++) {
            sb.append('[').append(i).append(":v]")
                    .append("scale=").append(layout.cellWidth()).append(':').append(layout.cellHeight())
                    .append(":force_original_aspect_ratio=decrease,")
                    .append("pad=").append(layout.cellWidth()).append(':').append(layout.cellHeight())
                    .append(":-1:-1:color=black,setsar=1,fps=").append(FPS)
                    .append("[c").append(i).append("];");
        }

        for (int r = 0; r < roundCount; r++) {
            int base = r * grid;
            if (grid == 1) {
                sb.append("[c").append(base).append("]null[round").append(r).append("];");
            } else if (layout.rows() == 1) {
                for (int k = 0; k < grid; k++) {
                    sb.append("[c").append(base + k).append(']');
                }
                sb.append("hstack=inputs=").append(grid).append("[round").append(r).append("];");
            } else {
                for (int row = 0; row < layout.rows(); row++) {
                    for (int col = 0; col < layout.cols(); col++) {
                        sb.append("[c").append(base + row * layout.cols() + col).append(']');
                    }
                    sb.append("hstack=inputs=").append(layout.cols())
                            .append("[row").append(r).append('_').append(row).append("];");
                }
                for (int row = 0; row < layout.rows(); row++) {
                    sb.append("[row").append(r).append('_').append(row).append(']');
                }
                sb.append("vstack=inputs=").append(layout.rows()).append("[round").append(r).append("];");
            }
        }

        for (int r = 0; r < roundCount; r++) {
            sb.append("[round").append(r).append(']');
        }
        sb.append("concat=n=").append(roundCount).append(":v=1:a=0[out]");
        return sb.toString();
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
