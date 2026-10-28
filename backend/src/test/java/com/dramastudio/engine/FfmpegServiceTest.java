package com.dramastudio.engine;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
class FfmpegServiceTest {
 @Test void buildsConcatCommand(){assertThat(FfmpegService.concatCommand(Path.of("list.txt"),Path.of("merged.mp4"),false)).containsExactly("-y","-hide_banner","-loglevel","error","-f","concat","-safe","0","-i","list.txt","-c","copy","merged.mp4");}
 @Test void buildsTrimCommand(){assertThat(FfmpegService.trimCommand(Path.of("in.mp4"),Path.of("out.mp4"),1.5,8.0,true)).containsSubsequence("-ss","1.5","-i","in.mp4","-to","8","-c:v","libx264","-c:a","aac","out.mp4");}
 @Test void buildsCoverScaleCommand(){assertThat(FfmpegService.scaleCommand(Path.of("in.mp4"),Path.of("out.mp4"),720,1280,"cover","#000000")).containsSubsequence("-vf","scale=720:1280:force_original_aspect_ratio=increase,crop=720:1280","-c:v","libx264");}
}
