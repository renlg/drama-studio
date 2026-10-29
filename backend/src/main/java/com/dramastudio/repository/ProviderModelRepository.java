package com.dramastudio.repository;

import com.dramastudio.entity.ProviderModel;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProviderModelRepository extends JpaRepository<ProviderModel, String> {
  List<ProviderModel> findByProviderIdOrderByCategoryAscSortOrderAsc(String providerId);
  List<ProviderModel> findByProviderIdAndCategoryOrderBySortOrderAsc(String providerId, String category);
  Optional<ProviderModel> findFirstByProviderIdAndCategoryAndIsDefaultOrderBySortOrderAsc(String providerId, String category, Integer isDefault);
  List<ProviderModel> findByEnabledOrderByProviderIdAscCategoryAscSortOrderAsc(Integer enabled);
  Optional<ProviderModel> findFirstByProviderIdAndCategoryAndModelId(String providerId, String category, String modelId);

  @Modifying
  @Query("update ProviderModel m set m.isDefault = 0 where m.providerId = :pid and m.category = :cat")
  void clearDefaultForCategory(@Param("pid") String providerId, @Param("cat") String category);
}
