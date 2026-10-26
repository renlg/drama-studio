package com.dramastudio.repository;
import com.dramastudio.entity.Project; import java.util.List; import org.springframework.data.jpa.repository.JpaRepository;
public interface ProjectRepository extends JpaRepository<Project,String>{ List<Project> findByOwnerUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(String owner); }
