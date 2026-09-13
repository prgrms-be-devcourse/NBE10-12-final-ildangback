package com.gommit.domain.checkin.support;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
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
    // 캔버스 가로 고정 — 칸 = CANVAS_WIDTH / 열수 (정사각). 세로는 행수에 따라 달라짐.
    // 1280: 폰 피드에서 보는 2초 영상엔 레티나 해상도로 충분 — 2160(4K급)은 메모리만 태우고 무의미
    // (2026-09-11 실측: 2160 은 ffmpeg RSS 가 back 컨테이너 한도를 넘겨 OOM-kill).
    private static final int CANVAS_WIDTH = 1280;
    private static final int FPS = 30; // 동영상 인증 대비해 30fps 로 통일
    private static final String PRESET = "veryfast";
    // 예전엔 통짜 120초 하나였는데, 회차별 렌더로 쪼개면서 회차당 예산 + concat(재인코딩 없음) 예산으로 분리.
    private static final long ROUND_TIMEOUT_SECONDS = 30;
    private static final long CONCAT_TIMEOUT_SECONDS = 15;

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

    // 캔버스 가로 = CANVAS_WIDTH(1280) 고정. 칸은 정사각(cellSize = CANVAS_WIDTH / cols), 세로 = rows * cellSize.
    // N=5 는 6칸 레이아웃을 쓰고 마지막 1칸은 항상 검정(gridCells > cellCount).
    record Layout(int cols, int rows, int cellSize) {

        int gridCells() {
            return cols * rows;
        }

        int canvasHeight() {
            return rows * cellSize;
        }

        static Layout forCells(int cellCount) {
            return switch (cellCount) {
                case 1 -> new Layout(1, 1, CANVAS_WIDTH); // 1280×1280
                case 2 -> new Layout(2, 1, CANVAS_WIDTH / 2); // 1280×640
                case 3 -> new Layout(3, 1, CANVAS_WIDTH / 3); // 1280×427(끝수 버림)
                case 4 -> new Layout(2, 2, CANVAS_WIDTH / 2); // 1280×1280
                case 5, 6 -> new Layout(3, 2, CANVAS_WIDTH / 3); // 1280×854 (5는 마지막 칸 검정)
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
            List<String> inputNames = writeFrames(workDir, flat, layout.cellSize());
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

    // 셀 크기로 사전 다운스케일해서 temp 에 쓴다. 원본(체크인 업로드, 최대 수천px)을 그대로 넘기면
    // ffmpeg 가 그 해상도 그대로 디코드해 xstack framesync 가 큰 프레임을 버퍼링 — 회차당
    // RSS 가 수백MB~1GB 로 뛰는 원인이었다(2026-09-11 실측). 여기서 미리 줄여두면 ffmpeg 는
    // 이미 셀 크기인 작은 이미지만 디코드한다.
    private List<String> writeFrames(Path workDir, List<Frame> flat, int cellSize) throws IOException {
        List<String> names = new ArrayList<>();
        int idx = 0;
        for (Frame frame : flat) {
            if (frame == null) {
                names.add(null);
                idx++;
                continue;
            }
            if (frame.kind() == Kind.IMAGE) {
                String name = "frame_%04d.jpg".formatted(idx++);
                writeDownscaledImage(frame.resource(), workDir.resolve(name), cellSize);
                names.add(name);
            } else {
                // 동영상 칸: 현재 미사용 경로(NOTE 참고) — 다운스케일 없이 원본 그대로.
                String name = "frame_%04d.%s".formatted(idx++, frame.extension());
                try (InputStream in = frame.resource().getInputStream()) {
                    Files.copy(in, workDir.resolve(name));
                }
                names.add(name);
            }
        }
        return names;
    }

    // cover 다운스케일(짧은 변을 셀에 맞추고 중앙 crop) 후 JPEG 로 저장. ffmpeg 필터의 scale+crop 과
    // 동일한 정규화를 앱단에서 먼저 해, ffmpeg 에는 이미 cellSize 인 작은 이미지만 들어가게 한다.
    // 디코드 실패 시(손상 파일 등) 원본을 그대로 복사 — graceful degrade.
    private void writeDownscaledImage(Resource resource, Path dest, int cellSize) throws IOException {
        BufferedImage src;
        try (InputStream in = resource.getInputStream()) {
            src = ImageIO.read(in);
        }
        if (src == null) {
            try (InputStream in = resource.getInputStream()) {
                Files.copy(in, dest);
            }
            return;
        }
        double scale = (double) cellSize / Math.min(src.getWidth(), src.getHeight());
        int w = Math.max(cellSize, (int) Math.round(src.getWidth() * scale));
        int h = Math.max(cellSize, (int) Math.round(src.getHeight() * scale));
        BufferedImage resized = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        int x = Math.max(0, (w - cellSize) / 2);
        int y = Math.max(0, (h - cellSize) / 2);
        BufferedImage cropped = resized.getSubimage(x, y, cellSize, cellSize);
        ImageIO.write(cropped, "jpg", dest.toFile());
    }

    // 회차별로 별도 ffmpeg 프로세스를 돌려 round_NN.mp4 를 만들고 마지막에 -c copy 로 이어붙인다.
    // 이전엔 grid×round(최대 48)개 입력을 filter_complex 하나에 동시에 열었는데, 그 구조 자체가
    // ffmpeg 가 인코딩 시작 전에 전부 초기화하며 메모리를 크게 무는 원인이었다(2026-09-11 실측).
    // 이제 한 ffmpeg 프로세스가 여는 입력은 grid(≤6)개로 고정 — 회차 수가 늘어도 peak RSS 는 안 늘어난다.
    // 회차 하나라도 실패/타임아웃하면 몽타주 전체를 포기한다(-1) — 새 상태 추적 없이 기존 재시도
    // 모델(DailyLogMontageScheduler sweep 이 videoKey null 인 걸 재선택)에 그대로 얹힌다.
    private int runFfmpeg(
            Path workDir, Layout layout, int roundCount, List<Frame> flat, List<String> inputNames, Path output)
            throws IOException, InterruptedException {
        int grid = layout.gridCells();
        List<String> roundFiles = new ArrayList<>();
        for (int r = 0; r < roundCount; r++) {
            int base = r * grid;
            List<Frame> roundFrames = flat.subList(base, base + grid);
            List<String> roundInputNames = inputNames.subList(base, base + grid);
            String roundFile = "round_%02d.mp4".formatted(r);
            Path roundOutput = workDir.resolve(roundFile);

            int exitCode = runRoundFfmpeg(workDir, layout, roundFrames, roundInputNames, roundOutput);
            if (exitCode != 0 || !Files.exists(roundOutput)) {
                log.warn("ffmpeg 회차 렌더 실패 — 몽타주 전체 포기 (round={}, exitCode={})", r, exitCode);
                return -1;
            }
            roundFiles.add(roundFile);
        }
        return concatRounds(workDir, roundFiles, output);
    }

    private int runRoundFfmpeg(
            Path workDir, Layout layout, List<Frame> roundFrames, List<String> roundInputNames, Path output)
            throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        command.add(ffmpegPath);
        command.add("-y");
        for (int i = 0; i < roundFrames.size(); i++) {
            Frame frame = roundFrames.get(i);
            if (frame == null) {
                // 검정 칸: ffmpeg의 lavfi 로 인라인 생성(임시 파일/추가 프로세스 불필요).
                command.add("-f");
                command.add("lavfi");
                command.add("-t");
                command.add(String.valueOf(ROUND_SECONDS));
                command.add("-i");
                command.add("color=c=black:s=%dx%d:r=%d".formatted(layout.cellSize(), layout.cellSize(), FPS));
            } else if (frame.kind() == Kind.VIDEO) {
                // NOTE: 현재 MediaType 에 VIDEO 가 없어 이 경로는 미사용 — 동영상 인증 도입 시 실제 클립으로 검증 필요.
                command.add("-t");
                command.add(String.valueOf(ROUND_SECONDS));
                command.add("-i");
                command.add(roundInputNames.get(i));
            } else {
                command.add("-loop");
                command.add("1");
                command.add("-t");
                command.add(String.valueOf(ROUND_SECONDS));
                command.add("-i");
                command.add(roundInputNames.get(i));
            }
        }
        command.add("-filter_complex");
        command.add(buildRoundFilter(layout, roundFrames));
        command.add("-map");
        command.add("[out]");
        command.add("-r");
        command.add(String.valueOf(FPS));
        command.add("-pix_fmt");
        command.add("yuv420p");
        command.add("-preset");
        command.add(PRESET);
        command.add(output.getFileName().toString());
        return run(workDir, command, ROUND_TIMEOUT_SECONDS);
    }

    // 스트림 복사(-c copy)만 하므로 재인코딩 없음 — 메모리·시간 거의 안 든다.
    private int concatRounds(Path workDir, List<String> roundFiles, Path output)
            throws IOException, InterruptedException {
        StringBuilder list = new StringBuilder();
        for (String f : roundFiles) {
            list.append("file '").append(f).append("'\n");
        }
        Files.writeString(workDir.resolve("concat.txt"), list.toString());
        List<String> command = List.of(
                ffmpegPath,
                "-y",
                "-f",
                "concat",
                "-safe",
                "0",
                "-i",
                "concat.txt",
                "-c",
                "copy",
                "-movflags",
                "+faststart",
                output.getFileName().toString());
        return run(workDir, command, CONCAT_TIMEOUT_SECONDS);
    }

    // 출력을 버린다(DISCARD) — 부모가 stdout/stderr 를 읽어가지 않으면 OS 파이프 버퍼가 차면서
    // ffmpeg 가 write 블로킹될 수 있다. 로그 소비처가 없어 병합(redirectErrorStream) 대신 폐기한다.
    private int run(Path workDir, List<String> command, long timeoutSeconds) throws IOException, InterruptedException {
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

    // 칸별 cover 정규화(짧은 변을 셀에 맞춰 스케일 후 중앙 crop, 동영상은 tpad 로 길이 보정) → xstack 그리드 한 장.
    // 입력은 이미 writeDownscaledImage 로 cellSize 로 줄어든 상태라 scale 은 사실상 no-op(안전망으로 유지).
    private static String buildRoundFilter(Layout layout, List<Frame> roundFrames) {
        int grid = layout.gridCells();
        int s = layout.cellSize();
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < roundFrames.size(); i++) {
            sb.append('[')
                    .append(i)
                    .append(":v]")
                    .append("scale=")
                    .append(s)
                    .append(':')
                    .append(s)
                    .append(":force_original_aspect_ratio=increase,")
                    .append("crop=")
                    .append(s)
                    .append(':')
                    .append(s)
                    .append(",setsar=1,fps=")
                    .append(FPS);
            Frame frame = roundFrames.get(i);
            if (frame != null && frame.kind() == Kind.VIDEO) {
                sb.append(",tpad=stop_mode=clone:stop_duration=")
                        .append(ROUND_SECONDS)
                        .append(",trim=duration=")
                        .append(ROUND_SECONDS)
                        .append(",setpts=PTS-STARTPTS");
            }
            sb.append("[c").append(i).append("];");
        }

        if (grid == 1) {
            sb.append("[c0]null[out]");
            return sb.toString();
        }
        for (int k = 0; k < grid; k++) {
            sb.append("[c").append(k).append(']');
        }
        sb.append("xstack=inputs=")
                .append(grid)
                .append(":layout=")
                .append(xstackLayout(layout))
                .append("[out]");
        return sb.toString();
    }

    // 균일한 정사각 칸 그리드를 xstack layout 픽셀 오프셋으로 나열. 예: 3×2, cellSize 720 →
    // "0_0|720_0|1440_0|0_720|720_720|1440_720". 칸 순서는 행 우선(row-major) — flatten() 이 채우는 순서와 같다.
    private static String xstackLayout(Layout layout) {
        StringBuilder sb = new StringBuilder();
        for (int k = 0; k < layout.gridCells(); k++) {
            if (k > 0) {
                sb.append('|');
            }
            int x = (k % layout.cols()) * layout.cellSize();
            int y = (k / layout.cols()) * layout.cellSize();
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
