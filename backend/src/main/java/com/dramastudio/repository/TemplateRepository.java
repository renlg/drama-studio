package com.dramastudio.repository;
import com.dramastudio.entity.Template; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface TemplateRepository extends JpaRepository<Template,String>{ List<Template> findByDeletedAtIsNullOrderByNodeType(); Optional<Template> findByNodeTypeAndScope(String type,String scope); }
