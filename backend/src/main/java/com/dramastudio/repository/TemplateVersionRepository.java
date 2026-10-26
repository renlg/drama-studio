package com.dramastudio.repository;
import com.dramastudio.entity.TemplateVersion; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface TemplateVersionRepository extends JpaRepository<TemplateVersion,String>{ Optional<TemplateVersion> findFirstByTemplateIdOrderByCreatedAtDesc(String id); }
