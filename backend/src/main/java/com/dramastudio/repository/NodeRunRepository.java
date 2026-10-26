package com.dramastudio.repository;
import com.dramastudio.entity.NodeRun; import java.util.*; import org.springframework.data.jpa.repository.JpaRepository;
public interface NodeRunRepository extends JpaRepository<NodeRun,String>{ List<NodeRun> findByWorkflowRunIdOrderByCreatedAt(String runId); }
