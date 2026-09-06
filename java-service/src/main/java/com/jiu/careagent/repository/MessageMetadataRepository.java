package com.jiu.careagent.repository;

import com.jiu.careagent.domain.MessageMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface MessageMetadataRepository extends JpaRepository<MessageMetadata, UUID> {
}
