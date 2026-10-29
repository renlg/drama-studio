package com.dramastudio.repository;
import com.dramastudio.entity.Toolbox;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ToolboxRepository extends JpaRepository<Toolbox,String>{List<Toolbox> findByUserIdOrderByCreatedAtDesc(String userId);Optional<Toolbox> findByIdAndUserId(String id,String userId);}
