package com.jiu.careagent.repository;

import com.jiu.careagent.domain.ServiceItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ServiceItemRepository extends JpaRepository<ServiceItem, UUID> {
    List<ServiceItem> findAllByEnabledTrueOrderByName();
    List<ServiceItem> findAllByEnabledTrueAndDistrictOrderByName(String district);
    List<ServiceItem> findAllByEnabledTrueAndCategoryOrderByName(String category);
    List<ServiceItem> findAllByEnabledTrueAndDistrictAndCategoryOrderByName(String district, String category);

    Optional<ServiceItem> findByIdAndEnabledTrue(UUID id);
}
