package vn.edu.wspm.backend.repositories.auth;

import java.util.Optional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import vn.edu.wspm.backend.entities.CustomerEntity;
import vn.edu.wspm.backend.entities.RoleEntity;
import vn.edu.wspm.backend.entities.UserAccountEntity;
import vn.edu.wspm.backend.entities.UserRoleEntity;
import vn.edu.wspm.backend.entities.UserRoleId;

@Repository
public class RegistrationRepository {
    @PersistenceContext private EntityManager em;

    public Optional<RoleEntity> customerRole() {
        return em.createQuery("select r from RoleEntity r where r.code = :code", RoleEntity.class)
                .setParameter("code", "CUSTOMER").getResultStream()
                .filter(r -> "CUSTOMER".equals(r.getCode())).findFirst();
    }

    public boolean identityExists(String username, String email) {
        return em.createQuery("select count(u) from UserAccountEntity u where u.username = :username or u.email = :email", Long.class)
                .setParameter("username", username).setParameter("email", email).getSingleResult() > 0;
    }

    public void persist(UserAccountEntity account, CustomerEntity customer, RoleEntity role) {
        em.persist(account); // Hibernate initializes @Version on this new entity.
        em.persist(customer);
        UserRoleId id = new UserRoleId();
        id.setUserId(account.getUserId());
        id.setRoleId(role.getRoleId());
        UserRoleEntity assignment = new UserRoleEntity();
        assignment.setId(id);
        assignment.setUser(account);
        assignment.setRole(role);
        assignment.setAssignedAt(account.getCreatedAt());
        em.persist(assignment);
        em.flush(); // Detect constraint races inside the owning transaction.
    }
}
