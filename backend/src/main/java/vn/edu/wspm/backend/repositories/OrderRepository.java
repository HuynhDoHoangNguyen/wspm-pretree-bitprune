package vn.edu.wspm.backend.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.wspm.backend.entities.OrderEntity;

public interface OrderRepository
        extends JpaRepository<OrderEntity, String> {

    List<OrderEntity> findByCustomerIdOrderByOrderTimeDesc(String customerId);

    List<OrderEntity> findByOrderStatus(String orderStatus);
}

