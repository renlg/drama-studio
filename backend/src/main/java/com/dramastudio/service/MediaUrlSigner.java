package com.dramastudio.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

@Service
public class MediaUrlSigner {
 private final byte[] key;
 private final String publicBase;

 public MediaUrlSigner(@Value("${app.jwt-secret}") String secret,
                       @Value("${app.public-base:/drama-studio}") String publicBase) {
  this.key=secret.getBytes(StandardCharsets.UTF_8);
  this.publicBase=publicBase.endsWith("/")?publicBase.substring(0,publicBase.length()-1):publicBase;
 }

 public String sign(String assetId,long ttlMillis){return sign(assetId,"",ttlMillis);}

 public String sign(String assetId,String variant,long ttlMillis){
  String normalized=variant==null?"":variant;
  long exp=System.currentTimeMillis()+ttlMillis;
  String mac=hmac(assetId+":"+normalized+":"+exp);
  String variantQuery=normalized.isBlank()?"":"variant="+UriUtils.encodeQueryParam(normalized,StandardCharsets.UTF_8)+"&";
  return publicBase+"/api/v1/media/"+UriUtils.encodePathSegment(assetId,StandardCharsets.UTF_8)+"?"+variantQuery+"exp="+exp+"&sig="+mac;
 }

 public boolean verify(String assetId,String exp,String sig){return verify(assetId,"",exp,sig);}

 public boolean verify(String assetId,String variant,String exp,String sig){
  if(exp==null||sig==null)return false;
  long expMs;
  try{expMs=Long.parseLong(exp);}catch(NumberFormatException e){return false;}
  if(System.currentTimeMillis()>expMs)return false;
  String normalized=variant==null?"":variant;
  String expected=hmac(assetId+":"+normalized+":"+expMs);
  return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),sig.getBytes(StandardCharsets.UTF_8));
 }

 private String hmac(String payload){
  try{
   Mac mac=Mac.getInstance("HmacSHA256");
   mac.init(new SecretKeySpec(key,"HmacSHA256"));
   return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
  }catch(Exception e){throw new IllegalStateException("签名失败",e);}
 }
}
