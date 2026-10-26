package com.dramastudio.repository;
import com.dramastudio.entity.WorkflowVersion; import java.util.Optional; import org.springframework.data.jpa.repository.JpaRepository;
public interface WorkflowVersionRepository extends JpaRepository<WorkflowVersion,String>{ Optional<WorkflowVersion> findFirstByWorkflowIdOrderByVersionNoDesc(String id); }
