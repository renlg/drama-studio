package com.dramastudio.repository;
import com.dramastudio.entity.User; import java.util.Optional; import org.springframework.data.jpa.repository.JpaRepository;
public interface UserRepository extends JpaRepository<User,String>{ Optional<User> findByAiConnectUsername(String username); }
