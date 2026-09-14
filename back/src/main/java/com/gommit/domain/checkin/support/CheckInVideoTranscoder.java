package com.gommit.domain.checkin.support;

import com.gommit.global.exception.BusinessException;
import com.gommit.global.exception.ErrorCode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

// 영상 체크인 업로드 원본(webm/mp4/mov, 브라우저·기기마다 다름)에 길이(2초), 해상도(1080), 파일타입(mp4)을 강제 정한다.
@Component
public class CheckInVideoTranscoder {

    private static final int CLIP_SECONDS = 2;
    private static final int MAX_EDGE = 1080;
    private static final double POSTER_TIMESTAMP_SECONDS = 1.0; // 중간 프레임 — 시작 직후(노출 조정)나 끝(정리 동작)보다 안정적
    private static final String PRESET = "veryfast";
    private static final long TRANSCODE_TIMEOUT_SECONDS = 20;
    private static final long POSTER_TIMEOUT_SECONDS = 10;

    private final String ffmpegPath;
    private final FfmpegProcessRunner processRunner;

    public CheckInVideoTranscoder(
            @Value("${app.dailylog.ffmpeg-path:ffmpeg}") String ffmpegPath, FfmpegProcessRunner processRunner) {
        this.ffmpegPath = ffmpegPath;
        this.processRunner = processRunner;
    }

    public record Transcoded(byte[] video, byte[] poster) {}

    public Transcoded transcode(MultipartFile file, String originalExtension) {
        try {
            return processRunner.withTempWorkDir("checkin-video-", workDir -> {
                Path input = workDir.resolve("input." + originalExtension);
                file.transferTo(input);

                Path video = workDir.resolve("out.mp4");
                int videoExit = processRunner.run(workDir, transcodeCommand(input, video), TRANSCODE_TIMEOUT_SECONDS);
                if (videoExit != 0 || !Files.exists(video)) {
                    throw new BusinessException(ErrorCode.MEDIA_STORAGE_FAILED);
                }

                Path poster = workDir.resolve("poster.jpg");
                int posterExit = processRunner.run(workDir, posterCommand(video, poster), POSTER_TIMEOUT_SECONDS);
                if (posterExit != 0 || !Files.exists(poster)) {
                    throw new BusinessException(ErrorCode.MEDIA_STORAGE_FAILED);
                }

                return new Transcoded(Files.readAllBytes(video), Files.readAllBytes(poster));
            });
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.MEDIA_STORAGE_FAILED);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.MEDIA_STORAGE_FAILED);
        }
    }

    // 업로드 원본이 CLIP_SECONDS 보다 짧을 수 있다(녹화 중 조기 종료 등) — padAndTrimFilter 가 마지막
    // 프레임을 정지 상태로 늘려 채우고 정확히 CLIP_SECONDS 에 맞춘다. 원본이 이미 CLIP_SECONDS 이상이면
    // (앞단 "-t" 가 먼저 잘라둠) 사실상 no-op.
    private List<String> transcodeCommand(Path input, Path output) {
        String filter =
                "scale=%1$d:%1$d:force_original_aspect_ratio=increase,crop=%1$d:%1$d,setsar=1,".formatted(MAX_EDGE)
                        + FfmpegProcessRunner.padAndTrimFilter(CLIP_SECONDS);
        return List.of(
                ffmpegPath,
                "-y",
                "-i",
                input.getFileName().toString(),
                "-t",
                String.valueOf(CLIP_SECONDS),
                "-vf",
                filter,
                "-an", // 오디오 트랙 제거 — FE 는 audio:false 로 녹화하지만 서버도 강제
                "-c:v",
                "libx264",
                "-preset",
                PRESET,
                "-pix_fmt",
                "yuv420p",
                "-movflags",
                "+faststart",
                output.getFileName().toString());
    }

    // 프레임 정확 seek 을 위해 -ss 를 -i 뒤에 둔다. 2초짜리라 시작부터 디코드해도 비용 무시할 만함
    // (input seeking 은 키프레임 단위라 짧은 클립(전체가 GOP 하나)에선 부정확할 수 있다).
    private List<String> posterCommand(Path video, Path poster) {
        return List.of(
                ffmpegPath,
                "-y",
                "-i",
                video.getFileName().toString(),
                "-ss",
                String.valueOf(POSTER_TIMESTAMP_SECONDS),
                "-frames:v",
                "1",
                poster.getFileName().toString());
    }
}
