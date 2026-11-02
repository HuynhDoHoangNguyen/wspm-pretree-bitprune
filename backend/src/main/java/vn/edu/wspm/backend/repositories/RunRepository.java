package vn.edu.wspm.backend.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.wspm.backend.entities.RunEntity;

public interface RunRepository
        extends JpaRepository<RunEntity, String> {

    List<RunEntity> findAllByOrderByRunTimeDesc();

    List<RunEntity> findByAlgorithm(String algorithm);
}

