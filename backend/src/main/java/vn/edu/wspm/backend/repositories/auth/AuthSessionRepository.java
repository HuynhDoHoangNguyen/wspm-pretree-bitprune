package vn.edu.wspm.backend.repositories.auth;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import vn.edu.wspm.backend.entities.UserAccountEntity;
import vn.edu.wspm.backend.entities.UserSessionEntity;

@Repository
public class AuthSessionRepository {
    @PersistenceContext private EntityManager em;

    public Optional<UserAccountEntity> byUsername(String username) {
        return em.createQuery("select u from UserAccountEntity u where u.username=:username",UserAccountEntity.class)
                .setParameter("username",username).getResultStream().findFirst();
    }
    public UserAccountEntity account(String id,boolean lock) {
        return lock ? em.find(UserAccountEntity.class,id,LockModeType.PESSIMISTIC_WRITE) : em.find(UserAccountEntity.class,id);
    }
    public Optional<String> ownerOfRefresh(String hash) {
        return em.createQuery("select s.user.userId from UserSessionEntity s where s.refreshTokenHash=:hash",String.class)
                .setParameter("hash",hash).getResultStream().findFirst();
    }
    public Optional<UserSessionEntity> lockRefresh(String hash) {
        // A locking/current read after the account lock; never reuse the earlier snapshot.
        return em.createQuery("select s from UserSessionEntity s where s.refreshTokenHash=:hash",UserSessionEntity.class)
                .setParameter("hash",hash).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultStream().findFirst();
    }
    public UserSessionEntity session(String id) { return em.find(UserSessionEntity.class,id); }
    public List<String> roles(String id) {
        return em.createQuery("select a.role.code from UserRoleEntity a where a.user.userId=:id",String.class)
                .setParameter("id",id).getResultStream().sorted().toList();
    }
    public void insert(UserSessionEntity session) { em.persist(session); }
    public void flush() { em.flush(); }
}
