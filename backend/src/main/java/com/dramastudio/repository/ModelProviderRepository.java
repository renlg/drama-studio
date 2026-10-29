package com.dramastudio.repository;

import com.dramastudio.entity.ModelProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelProviderRepository extends JpaRepository<ModelProvider, String> {
  List<ModelProvider> findAllByOrderBySortOrderAscCreatedAtAsc();
  Optional<ModelProvider> findFirstByIsDefaultOrderBySortOrderAsc(Integer isDefault);
  List<ModelProvider> findByEnabledOrderBySortOrderAsc(Integer enabled);
}
