package com.dramastudio.engine;

import com.dramastudio.gateway.ProviderProtocol.ErrorCode;
import com.dramastudio.gateway.ProviderProtocol.ProviderException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

@Service
public class FfmpegService {
  private final String configuredBin;

  public FfmpegService(Environment env) {
    this.configuredBin = env.getProperty("app.ffmpeg.bin", "");
  }

  public String binary() {
    List<String> candidates = new ArrayList<>();
    if (!configuredBin.isBlank()) candidates.add(configuredBin);
    String env = System.getenv("FFMPEG_BIN");
    if (env != null && !env.isBlank()) candidates.add(env);
    candidates.add("/usr/bin/ffmpeg");
    candidates.add("/usr/local/bin/ffmpeg");
    for (String candidate : candidates) if (Files.isExecutable(Path.of(candidate))) return candidate;
    try {
      Process p = new ProcessBuilder("python3", "-c", "import imageio_ffmpeg;print(imageio_ffmpeg.get_ffmpeg_exe())")
          .redirectErrorStream(true).start();
      String found = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
      if (p.waitFor() == 0 && !found.isBlank() && Files.isExecutable(Path.of(found))) return found;
    } catch (Exception ignored) {
    }
    throw new ProviderException(ErrorCode.PROVIDER_TIMEOUT, "未找到 ffmpeg，请配置 app.ffmpeg.bin");
  }

  public void run(List<String> args) {
    List<String> command = new ArrayList<>();
    command.add(binary());
    command.addAll(args);
    try {
      Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
      List<String> lines = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).lines().toList();
      int exit = process.waitFor();
      if (exit != 0) {
        int from = Math.max(0, lines.size() - 20);
        throw new ProviderException(ErrorCode.PROVIDER_TIMEOUT,
            "ffmpeg 执行失败(exit=" + exit + "):\n" + String.join("\n", lines.subList(from, lines.size())));
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ProviderException(ErrorCode.PROVIDER_TIMEOUT, "ffmpeg 执行被中断", e);
    } catch (IOException e) {
      throw new ProviderException(ErrorCode.PROVIDER_TIMEOUT, "ffmpeg 启动失败: " + e.getMessage(), e);
    }
  }

  public double duration(Path input) {
    try {
      Process p=new ProcessBuilder(binary(),"-hide_banner","-i",input.toString(),"-f","null","-").redirectErrorStream(true).start();
      String text=new String(p.getInputStream().readAllBytes(),StandardCharsets.UTF_8);p.waitFor();
      java.util.regex.Matcher m=java.util.regex.Pattern.compile("Duration: (\\d+):(\\d+):(\\d+(?:\\.\\d+)?)").matcher(text);
      if(!m.find())throw new IllegalStateException("无法读取媒体时长");return Integer.parseInt(m.group(1))*3600+Integer.parseInt(m.group(2))*60+Double.parseDouble(m.group(3));
    } catch (InterruptedException e) {Thread.currentThread().interrupt();throw new ProviderException(ErrorCode.PROVIDER_TIMEOUT,"读取媒体时长被中断",e);}
      catch(Exception e){throw new ProviderException(ErrorCode.PROVIDER_TIMEOUT,"读取媒体时长失败: "+e.getMessage(),e);}
  }

  private static List<String> base() { return new ArrayList<>(List.of("-y", "-hide_banner", "-loglevel", "error")); }

  public static List<String> concatCommand(Path list, Path output, boolean reencode) {
    List<String> a=base();Collections.addAll(a,"-f","concat","-safe","0","-i",list.toString());
    if(reencode)Collections.addAll(a,"-c:v","libx264","-c:a","aac");else Collections.addAll(a,"-c","copy");
    a.add(output.toString());return a;
  }
  public static List<String> fadeConcatCommand(List<Path> inputs,List<Double> durations,Path output,double transition){
    if(inputs.size()<2)throw new IllegalArgumentException("fade 至少需要两个视频");List<String>a=base();for(Path input:inputs)Collections.addAll(a,"-i",input.toString());StringBuilder f=new StringBuilder();double elapsed=durations.get(0);String previous="0:v";
    for(int i=1;i<inputs.size();i++){double offset=Math.max(0,elapsed-transition);String out="v"+i;f.append('[').append(previous).append("]").append('[').append(i).append(":v]").append("xfade=transition=fade:duration=").append(fmt(transition)).append(":offset=").append(fmt(offset)).append('[').append(out).append("];");previous=out;elapsed+=durations.get(i)-transition;}
    f.setLength(f.length()-1);Collections.addAll(a,"-filter_complex",f.toString(),"-map","["+previous+"]","-c:v","libx264","-an",output.toString());return a;
  }
  public static List<String> trimCommand(Path input,Path output,double start,Double end,boolean reencode){
    List<String>a=base();Collections.addAll(a,"-ss",fmt(start),"-i",input.toString());if(end!=null)Collections.addAll(a,"-to",fmt(end));
    if(reencode)Collections.addAll(a,"-c:v","libx264","-c:a","aac");else Collections.addAll(a,"-c","copy");a.add(output.toString());return a;
  }
  public static List<String> scaleCommand(Path input,Path output,int width,int height,String fit,String color){
    String filter=switch(fit){case"contain"->"scale="+width+":"+height+":force_original_aspect_ratio=decrease,pad="+width+":"+height+":(ow-iw)/2:(oh-ih)/2:"+color;case"stretch"->"scale="+width+":"+height;default->"scale="+width+":"+height+":force_original_aspect_ratio=increase,crop="+width+":"+height;};
    List<String>a=base();Collections.addAll(a,"-i",input.toString(),"-vf",filter,"-c:v","libx264","-c:a","copy",output.toString());return a;
  }
  public static List<String> muxCommand(Path video,Path audio,Path output,double volume,boolean keep,double offset,boolean shortest){
    List<String>a=base();Collections.addAll(a,"-i",video.toString(),"-itsoffset",fmt(offset),"-i",audio.toString());
    if(keep)Collections.addAll(a,"-filter_complex","[0:a][1:a]amix=inputs=2:weights='1 "+fmt(volume)+"'[a]","-map","0:v","-map","[a]");
    else Collections.addAll(a,"-filter:a:1","volume="+fmt(volume),"-map","0:v","-map","1:a");
    Collections.addAll(a,"-c:v","copy","-c:a","aac");if(shortest)a.add("-shortest");a.add(output.toString());return a;
  }
  public static List<String> extractCommand(Path input,Path outputPattern,String mode,double at,double every,int max){
    List<String>a=base();if("single".equals(mode))Collections.addAll(a,"-ss",fmt(at));if("last".equals(mode))Collections.addAll(a,"-sseof","-0.1");Collections.addAll(a,"-i",input.toString());
    if("first".equals(mode))Collections.addAll(a,"-vf","select=eq(n\\,0)","-frames:v","1");
    else if("last".equals(mode))Collections.addAll(a,"-frames:v","1");
    else if("every_n".equals(mode))Collections.addAll(a,"-vf","fps=1/"+fmt(every),"-frames:v",String.valueOf(max));
    else Collections.addAll(a,"-frames:v","1");a.add(outputPattern.toString());return a;
  }
  public static List<String> chromaCommand(Path input,Path output,String color,double similarity,double blend){List<String>a=base();String c=color.replace("#","0x");Collections.addAll(a,"-i",input.toString(),"-vf","chromakey=color="+c+":similarity="+fmt(similarity)+":blend="+fmt(blend),output.toString());return a;}
  public static List<String> interpolateCommand(Path input,Path output,int fps,String mode){String filter="dup".equals(mode)?"fps="+fps:"minterpolate=fps="+fps+":mi_mode="+("blend".equals(mode)?"blend":"mci");List<String>a=base();Collections.addAll(a,"-i",input.toString(),"-vf",filter,"-c:v","libx264","-c:a","copy",output.toString());return a;}
  private static String fmt(double value){return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();}
}
