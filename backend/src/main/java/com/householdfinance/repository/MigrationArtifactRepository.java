package com.householdfinance.repository;

import com.householdfinance.entity.MigrationArtifactEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MigrationArtifactRepository extends JpaRepository<MigrationArtifactEntity, Long> {

    List<MigrationArtifactEntity> findByImportIdOrderByCreatedAtAsc(String importId);

    Optional<MigrationArtifactEntity> findTopByImportIdAndArtifactTypeOrderByCreatedAtDesc(String importId, String artifactType);

    Optional<MigrationArtifactEntity> findTopByArtifactTypeOrderByCreatedAtDesc(String artifactType);
}
