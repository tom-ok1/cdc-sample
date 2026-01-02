package com.example.consumer.repository;

import com.example.consumer.entity.DocumentStoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DocumentStoreRepository extends JpaRepository<DocumentStoreEntity, String> {
}
