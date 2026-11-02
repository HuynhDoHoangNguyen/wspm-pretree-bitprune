package vn.edu.wspm.backend.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.edu.wspm.backend.entities.OrderItemEntity;
import vn.edu.wspm.backend.entities.OrderItemId;

public interface OrderItemRepository
        extends JpaRepository<OrderItemEntity, OrderItemId> {

    List<OrderItemEntity> findByOrderId(String orderId);

    List<OrderItemEntity> findByProductId(String productId);
}

