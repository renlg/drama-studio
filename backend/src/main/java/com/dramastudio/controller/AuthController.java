package com.dramastudio.controller;
import com.dramastudio.repository.UserRepository; import com.dramastudio.service.AuthService; import jakarta.servlet.http.*; import java.net.*; import java.util.*; import org.springframework.beans.factory.annotation.Value; import org.springframework.http.*; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*; import org.springframework.web.server.ResponseStatusException;
@RestController public class AuthController {
 private final AuthService auth; private final UserRepository users; @Value("${app.oauth.client-id}")String clientId; @Value("${app.oauth.redirect-uri}")String redirect; @Value("${app.oauth.authorize-url}")String authorize; @Value("${app.frontend-url}")String frontend;
 public AuthController(AuthService a,UserRepository u){auth=a;users=u;}
 @GetMapping("/auth/login") public void login(HttpSession s,HttpServletResponse r)throws Exception{String state=UUID.randomUUID().toString();s.setAttribute("oauth_state",state);String url=authorize+"?client_id="+enc(clientId)+"&redirect_uri="+enc(redirect)+"&response_type=code&state="+enc(state);r.sendRedirect(url);}
 @GetMapping("/auth/callback") public void callback(@RequestParam String code,@RequestParam String state,HttpSession s,HttpServletResponse r)throws Exception{if(!Objects.equals(state,s.getAttribute("oauth_state")))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid OAuth state");s.removeAttribute("oauth_state");String token=auth.exchange(code);r.sendRedirect(frontend+"/login-callback?token="+enc(token));}
 @PostMapping("/auth/logout") public ResponseEntity<Void> logout(HttpSession s){s.invalidate();return ResponseEntity.noContent().build();}
 @GetMapping("/api/v1/me") public Object me(Authentication a){return users.findById(a.getName()).orElseThrow();}
 private String enc(String s){return URLEncoder.encode(s,java.nio.charset.StandardCharsets.UTF_8);}
}
