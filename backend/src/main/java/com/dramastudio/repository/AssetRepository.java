package com.dramastudio.repository;
import com.dramastudio.entity.Asset; import java.util.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param;
public interface AssetRepository extends JpaRepository<Asset,String>{
 List<Asset> findByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(String projectId);
 Optional<Asset> findFirstByProjectIdAndLogicalKeyOrderByVersionNoDesc(String p,String k);
 @Modifying @Query("update Asset a set a.selected=false where a.projectId=:p and a.logicalKey=:k") int clearSelection(@Param("p") String p,@Param("k") String k);
}
