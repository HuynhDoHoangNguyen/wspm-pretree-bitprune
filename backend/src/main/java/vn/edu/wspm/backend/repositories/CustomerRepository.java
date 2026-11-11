package vn.edu.wspm.backend.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.wspm.backend.entities.CustomerEntity;

public interface CustomerRepository
        extends JpaRepository<CustomerEntity, String> {
}

