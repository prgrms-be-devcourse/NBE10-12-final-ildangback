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

@Slf4j
@Component
public class DailyLogMontageBuilder {

    // 그리드 최대 칸 수 — 챌린지 인원 상한(1~6)과 같다.
    public static final int MAX_CELLS = 6;

    private static final int ROUND_SECONDS = 2; // 회차 1개 노출 시간
    private static final int CANVAS_WIDTH = 1080; // 9:16 세로 — 모바일 풀스크린
    private static final int CANVAS_HEIGHT = 1920;
    private static final int FPS = 30; // 동영상 인증 대비해 30fps 로 통일
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

    private static List<Frame> flatten(int cellCount, Layout layout, List<List<Frame>> rounds) {
        List<Frame> flat = new ArrayList<>();
        for (List<Frame> slots : rounds) {
            for (int cell = 0; cell < layout.gridCells(); cell++) {
                flat.add(cell < cellCount ? slots.get(cell) : null);
            }
        }
        return flat;
    }

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
            Path workDir, Layout layout, int roundCount, List<Frame> flat, List<String> inputNames, Path output)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");

        for (int i = 0; i < flat.size(); i++) {
            Frame frame = flat.get(i);
            if (frame == null) {
                // 검정 칸: ffmpeg의 lavfi 로 인라인 생성(임시 파일/추가 프로세스 불필요).
                command.add("-f");
                command.add("lavfi");
                command.add("-t");
                command.add(String.valueOf(ROUND_SECONDS));
                command.add("-i");
                command.add("color=c=black:s=%dx%d:r=%d".formatted(layout.cellWidth(), layout.cellHeight(), FPS));
            } else if (frame.kind() == Kind.VIDEO) {
                // 동영상 칸: 긴 클립은 -t 로 앞 ROUND_SECONDS 초만 디코드. 짧은 클립은 buildFilter 의 tpad 로 보정.
                // NOTE: 현재 MediaType 에 VIDEO 가 없어 이 경로는 미사용 — 동영상 인증 도입 시 실제 클립으로 검증 필요.
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
        command.add(buildFilter(layout, roundCount, flat));
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

    // 칸별 정규화(scale+pad+fps, 동영상은 tpad 로 길이 보정) → 회차별 xstack 그리드 → 회차 concat.
    private static String buildFilter(Layout layout, int roundCount, List<Frame> flat) {
        int grid = layout.gridCells();
        int cw = layout.cellWidth();
        int ch = layout.cellHeight();
        String cellLayout = xstackLayout(layout);
        StringBuilder sb = new StringBuilder();

        // 모든 입력을 셀 크기로 정규화. 사진 전체가 보이게 축소(decrease) 후 칸 안쪽만 검정 pad.
        for (int i = 0; i < flat.size(); i++) {
            sb.append('[')
                    .append(i)
                    .append(":v]")
                    .append("scale=")
                    .append(cw)
                    .append(':')
                    .append(ch)
                    .append(":force_original_aspect_ratio=decrease,")
                    .append("pad=")
                    .append(cw)
                    .append(':')
                    .append(ch)
                    .append(":-1:-1:color=black,setsar=1,fps=")
                    .append(FPS);
            Frame frame = flat.get(i);
            if (frame != null && frame.kind() == Kind.VIDEO) {
                // 클립이 ROUND_SECONDS 보다 짧으면 마지막 프레임을 복제해 채우고, 그다음 정확히 잘라낸다.
                // (동영상 인증 미도입 상태 — 실제 클립으로 검증 필요)
                sb.append(",tpad=stop_mode=clone:stop_duration=")
                        .append(ROUND_SECONDS)
                        .append(",trim=duration=")
                        .append(ROUND_SECONDS)
                        .append(",setpts=PTS-STARTPTS");
            }
            sb.append("[c").append(i).append("];");
        }

        // 회차별로 grid 칸을 하나의 프레임으로 합친다. xstack 단일 필터가 모든 레이아웃(1~6칸)을 커버.
        for (int r = 0; r < roundCount; r++) {
            int base = r * grid;
            if (grid == 1) {
                sb.append("[c").append(base).append("]null[round").append(r).append("];");
                continue;
            }
            for (int k = 0; k < grid; k++) {
                sb.append("[c").append(base + k).append(']');
            }
            sb.append("xstack=inputs=")
                    .append(grid)
                    .append(":layout=")
                    .append(cellLayout)
                    .append("[round")
                    .append(r)
                    .append("];");
        }

        for (int r = 0; r < roundCount; r++) {
            sb.append("[round").append(r).append(']');
        }
        sb.append("concat=n=").append(roundCount).append(":v=1:a=0[out]");
        return sb.toString();
    }

    // 균일한 칸 그리드를 xstack layout 픽셀 오프셋으로 나열. 예: 2×3 → "0_0|540_0|0_640|540_640|0_1280|540_1280".
    // 칸 순서는 행 우선(row-major) — flatten() 이 채우는 순서와 같다.
    private static String xstackLayout(Layout layout) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < layout.gridCells(); k++) {
            if (k > 0) {
                sb.append('|');
            }
            int x = (k % layout.cols()) * layout.cellWidth();
            int y = (k / layout.cols()) * layout.cellHeight();
            sb.append(x).append('_').append(y);
        }
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
