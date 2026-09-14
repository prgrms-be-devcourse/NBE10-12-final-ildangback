package com.gommit.domain.checkin.support;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

// ffmpeg 로 돌아가는 테스트들이 공유하는 유무 판정 + 합성 영상 생성 + 결과 mp4 길이 측정.
// CheckInVideoTranscoderTest/DailyLogMontageBuilderFfmpegTest 는 같은 패키지라 상속으로 쓰고,
// CheckInApiIntegrationTest/CheckInApiCloudinaryIntegrationTest 는 이미 IntegrationTestSupport 를
// 상속하고 있어(다중상속 불가) 이 클래스를 정적 호출로만 쓴다 — 그래서 public.
// 전부 ffmpeg 이 PATH 에 없으면(CI 등) 스킵되는 걸 전제로 한다.
public abstract class AbstractFfmpegTest {

    public static boolean ffmpegAvailable() {
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

    // lavfi testsrc 로 합성 영상을 만든다 — 실 카메라 파일 없이도 지정한 길이의 mp4 를 얻는다.
    public static byte[] syntheticVideoBytes(double durationSeconds) throws Exception {
        Path tmp = Files.createTempFile("ffmpeg-test-synth-", ".mp4");
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
            return Files.readAllBytes(tmp);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    // 인코딩된 mp4 바이트를 임시 파일로 떨궈 ffprobe 로 재생시간(초)을 읽는다.
    public static double probeDuration(byte[] mp4) throws Exception {
        Path tmp = Files.createTempFile("ffmpeg-test-probe-", ".mp4");
        try {
            Files.write(tmp, mp4);
            Process p = new ProcessBuilder(
                            "ffprobe",
                            "-v",
                            "error",
                            "-show_entries",
                            "format=duration",
                            "-of",
                            "default=noprint_wrappers=1:nokey=1",
                            tmp.toString())
                    .redirectErrorStream(true)
                    .start();
            String line;
            try (BufferedReader r =
                    new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                line = r.readLine();
            }
            p.waitFor();
            return line == null ? -1 : Double.parseDouble(line.trim());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
