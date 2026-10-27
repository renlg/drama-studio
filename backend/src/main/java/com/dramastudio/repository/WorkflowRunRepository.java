package com.dramastudio.repository;
import com.dramastudio.entity.WorkflowRun; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface WorkflowRunRepository extends JpaRepository<WorkflowRun,String>{ List<WorkflowRun> findAllByOrderByCreatedAtDesc(); List<WorkflowRun> findByProjectIdOrderByCreatedAtDesc(String projectId); }
