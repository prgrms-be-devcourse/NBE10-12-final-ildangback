package com.gommit.domain.checkin.support;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

// CheckInVideoTranscoderTest 와 DailyLogMontageBuilderFfmpegTest 가 공유하는 ffmpeg 유무 판정 +
// 결과 mp4 길이 측정. 둘 다 실제 로컬 ffmpeg 로 돌아가고, ffmpeg 이 PATH 에 없으면(CI 등) 스킵한다.
abstract class AbstractFfmpegTest {

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

    // 인코딩된 mp4 바이트를 임시 파일로 떨궈 ffprobe 로 재생시간(초)을 읽는다.
    static double probeDuration(byte[] mp4) throws Exception {
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
