package com.dramastudio.repository;
import com.dramastudio.entity.Workflow; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface WorkflowRepository extends JpaRepository<Workflow,String>{ List<Workflow> findByProjectIdAndDeletedAtIsNull(String projectId); }
