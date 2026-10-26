package com.dramastudio.service;
import org.springframework.security.core.Authentication; import org.springframework.stereotype.Component;
@Component public class CurrentUser { public String id(Authentication a){if(a==null||!a.isAuthenticated())throw new org.springframework.security.access.AccessDeniedException("Authentication required");return a.getName();} }
