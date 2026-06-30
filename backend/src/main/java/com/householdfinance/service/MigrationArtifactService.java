package com.householdfinance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.householdfinance.entity.MigrationArtifactEntity;
import com.householdfinance.repository.MigrationArtifactRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@SuppressWarnings("null")
public class MigrationArtifactService {

    public record ArtifactHistoryEntry(Long id, String importId, String artifactType, Instant createdAt, String payload) {}

    private final MigrationArtifactRepository migrationArtifactRepository;
    private final ObjectMapper objectMapper;

    public MigrationArtifactService(MigrationArtifactRepository migrationArtifactRepository, ObjectMapper objectMapper) {
        this.migrationArtifactRepository = migrationArtifactRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void recordArtifact(String importId, String artifactType, Object payload) {
        var entity = new MigrationArtifactEntity();
        entity.setImportId(importId);
        entity.setArtifactType(artifactType);
        entity.setCreatedAt(Instant.now());
        entity.setPayload(serialize(payload));
        migrationArtifactRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public <T> T getLatestPayload(String importId, String artifactType, Class<T> payloadType) {
        return migrationArtifactRepository.findTopByImportIdAndArtifactTypeOrderByCreatedAtDesc(importId, artifactType)
                .map(MigrationArtifactEntity::getPayload)
                .map(payload -> deserialize(payload, payloadType))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public <T> T getLatestPayloadByArtifactType(String artifactType, Class<T> payloadType) {
        return migrationArtifactRepository.findTopByArtifactTypeOrderByCreatedAtDesc(artifactType)
                .map(MigrationArtifactEntity::getPayload)
                .map(payload -> deserialize(payload, payloadType))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public List<ArtifactHistoryEntry> getHistory(String importId) {
        return migrationArtifactRepository.findByImportIdOrderByCreatedAtAsc(importId).stream()
                .map(entity -> new ArtifactHistoryEntry(
                        entity.getId(),
                        entity.getImportId(),
                        entity.getArtifactType(),
                        entity.getCreatedAt(),
                        entity.getPayload()
                ))
                .toList();
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize migration artifact payload.", exception);
        }
    }

    private <T> T deserialize(String payload, Class<T> payloadType) {
        try {
            return objectMapper.readValue(payload, payloadType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to deserialize migration artifact payload.", exception);
        }
    }
}
