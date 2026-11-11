package vn.edu.wspm.backend.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import vn.edu.wspm.backend.entities.RunEntity;

public interface RunRepository
        extends JpaRepository<RunEntity, String>, JpaSpecificationExecutor<RunEntity> {

    List<RunEntity> findAllByOrderByRunTimeDesc();

    List<RunEntity> findByAlgorithm(String algorithm);
}

