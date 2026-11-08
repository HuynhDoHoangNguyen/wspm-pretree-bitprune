import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.edu.wspm.backend.config.auth.AuthConfiguration;

/** Explicit local/demo operation; never registered as an application bean or migration. */
public class DemoAccounts {
    static final String SOURCE_SHA="8748da37961a0413628629a6b3e1809baec2862925dac86d4391731ab051df8a";
    record Customer(String username,String id,String anonymous) {}
    record Account(String id,String username,String email,String name,String status,String hash) {
        @Override public String toString() { return "Account[redacted]"; }
    }
    record Target(String id,String username,String email,String name,String customerId,Account before,
                  Set<String> roles,boolean passwordMatches,boolean changed) {
        @Override public String toString() { return "Target[redacted]"; }
    }
    static final PasswordEncoder ENCODER=new AuthConfiguration().authPasswordEncoder();
    static Properties config=new Properties();

    public static void main(String[] args) {
        try {
            if(args.length!=2 || !Set.of("check","apply","verify").contains(args[0])) throw fail("USAGE: check|apply|verify repository-root");
            Path root=Path.of(args[1]).toRealPath();
            Path dotenv=root.resolve(".env");
            if(Files.exists(dotenv)) try(var r=Files.newBufferedReader(dotenv,StandardCharsets.UTF_8)) { config.load(r); }
            String database=required("MYSQL_DATABASE"), port=value("MYSQL_PORT","3307");
            if(!database.equals("wspm_combo") || !port.equals("3307")) throw fail("LOCAL_ENDPOINT_MISMATCH");
            String expected=required("DEMO_EXPECTED_SERVER_UUID");
            String adminPassword=required("DEMO_ADMIN_PASSWORD"), customerPassword=required("DEMO_CUSTOMER_PASSWORD");
            if(!required("DEMO_ACCOUNT_SCOPE").equals("LOCAL_DEMO_ONLY")) throw fail("LOCAL_SCOPE_REQUIRED");
            List<Customer> manifest=manifest(root);
            try(Connection c=DriverManager.getConnection("jdbc:mysql://127.0.0.1:"+port+"/"+database+"?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true",
                    required("MYSQL_USER"),required("MYSQL_PASSWORD"))) {
                if(!expected.equals(scalar(c,"SELECT @@server_uuid")) || !database.equals(c.getCatalog())) throw fail("DATABASE_IDENTITY_MISMATCH");
                if(!"8.4.13".equals(scalar(c,"SELECT @@version"))) throw fail("SERVER_VERSION_MISMATCH");
                if(!"2".equals(scalar(c,"SELECT version FROM flyway_schema_history WHERE success=1 ORDER BY installed_rank DESC LIMIT 1"))) throw fail("V2_HISTORY_REQUIRED");
                if(!"5".equals(scalar(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND engine='InnoDB' AND table_name IN ('USER_ACCOUNT','CUSTOMER','ROLE','USER_ROLE','USER_SESSION')"))) throw fail("SCHEMA_ENGINE_MISMATCH");
                System.out.println("Identity/schema: VERIFIED local 127.0.0.1:3307/"+database+" server_uuid="+expected);
                if(!"1".equals(scalar(c,"SELECT GET_LOCK('tv2-acc-01-wspm-combo',10)"))) throw fail("OPERATION_LOCK_UNAVAILABLE");
                try {
                    c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                    c.setAutoCommit(false);
                    boolean apply=args[0].equals("apply");
                    List<Target> targets=plan(c,manifest,adminPassword,customerPassword,apply);
                    int created=0,updated=0,unchanged=0,revoked=0,rolesCreated=0;
                    // Complete preflight above precedes every mutation, including role provisioning.
                    if(apply) {
                        Map<String,String> roleIds=new HashMap<>();
                        for(String code:List.of("ADMIN","CUSTOMER")) {
                            String roleId=scalar(c,"SELECT role_id FROM ROLE WHERE code=?",code);
                            if(roleId==null) {
                                roleId="demo-role-"+code.toLowerCase(Locale.ROOT);
                                if(scalar(c,"SELECT role_id FROM ROLE WHERE role_id=?",roleId)!=null) throw fail("ROLE_ID_COLLISION");
                                update(c,"INSERT INTO ROLE(role_id,code,name) VALUES(?,?,?)",roleId,code,code);rolesCreated++;
                            } else if(!code.equals(scalar(c,"SELECT code FROM ROLE WHERE role_id=?",roleId))) throw fail("ROLE_CODE_CASE_CONFLICT");
                            roleIds.put(code,roleId);
                        }
                        for(Target t:targets) {
                            String password=t.customerId()==null?adminPassword:customerPassword;
                            if(t.before()==null) {
                                update(c,"INSERT INTO USER_ACCOUNT(user_id,username,email,password_hash,display_name,status,version,created_at,updated_at) VALUES(?,?,?,?,?,'ACTIVE',0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                                        t.id(),t.username(),t.email(),ENCODER.encode(password),t.name());created++;
                            } else if(t.changed()) {
                                String hash=t.passwordMatches()?t.before().hash():ENCODER.encode(password);
                                update(c,"UPDATE USER_ACCOUNT SET email_verified_at=CASE WHEN email <=> ? THEN email_verified_at ELSE NULL END,username=?,email=?,display_name=?,password_hash=?,status='ACTIVE',version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE user_id=?",
                                        t.email(),t.username(),t.email(),t.name(),hash,t.id());updated++;
                                // Same revoked_at contract as AuthSessionService; account locks precede session updates.
                                revoked+=update(c,"UPDATE USER_SESSION SET revoked_at=UTC_TIMESTAMP(6) WHERE user_id=? AND revoked_at IS NULL",t.id());
                            } else unchanged++;
                            if(!roles(c,t.id()).equals(t.roles())) {
                                for(var role:rows(c,"SELECT role_id FROM USER_ROLE WHERE user_id=?",t.id()))
                                    if(!t.roles().contains(scalar(c,"SELECT code FROM ROLE WHERE role_id=?",role.get(0))))
                                        update(c,"DELETE FROM USER_ROLE WHERE user_id=? AND role_id=?",t.id(),role.get(0));
                                for(String code:t.roles()) if(scalar(c,"SELECT role_id FROM USER_ROLE WHERE user_id=? AND role_id=?",t.id(),roleIds.get(code))==null)
                                    update(c,"INSERT INTO USER_ROLE(user_id,role_id,assigned_at,assigned_by) VALUES(?,?,UTC_TIMESTAMP(6),NULL)",t.id(),roleIds.get(code));
                            }
                            if(t.customerId()!=null) update(c,"UPDATE CUSTOMER SET user_id=? WHERE customer_id=? AND user_id IS NULL",t.id(),t.customerId());
                        }
                        // Readback must pass before commit. Any error closes the uncommitted connection: rollback.
                        verify(c,targets,adminPassword,customerPassword);
                        c.commit();
                        System.out.printf("APPLIED created=%d updated=%d unchanged=%d rolesCreated=%d revokedSessions=%d mappings=100%n",created,updated,unchanged,rolesCreated,revoked);
                    } else {
                        long changes=targets.stream().filter(Target::changed).count();
                        for(Target t:targets) System.out.println("MAP\t"+(t.customerId()==null?"ADMIN":t.customerId())+"\t"+t.id()+"\t"+t.username());
                        if(args[0].equals("verify")) verify(c,targets,adminPassword,customerPassword);
                        c.rollback();
                        System.out.println(args[0].toUpperCase(Locale.ROOT)+" targets=101 mappings=100 pendingChanges="+changes+" collisions=0");
                    }
                } catch(Exception failure) {
                    c.rollback();throw failure;
                } finally { try { c.setAutoCommit(true);scalar(c,"SELECT RELEASE_LOCK('tv2-acc-01-wspm-combo')"); } catch(SQLException ignored) {} }
            }
        } catch(Exception failure) {
            // Never print exception causes, SQL, account hashes, passwords or JDBC URLs with secrets.
            System.err.println("DEMO_ACCOUNTS_FAILED "+(failure instanceof SafeFailure?failure.getMessage():failure.getClass().getSimpleName()));
            System.exit(1);
        }
    }

    static List<Customer> manifest(Path root) throws Exception {
        String actual=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root.resolve("seeder.sql"))));
        if(!actual.equals(SOURCE_SHA)) throw fail("SEED_ARTIFACT_CHANGED_REVIEW_REQUIRED");
        var lines=Files.readAllLines(root.resolve("backend/scripts/auth/demo-customers.tsv"),StandardCharsets.UTF_8);
        var result=new ArrayList<Customer>();var ids=new HashSet<String>();
        for(String line:lines) {
            if(line.startsWith("#") || line.startsWith("username\t") || line.isBlank()) continue;
            String[] v=line.split("\t",-1);
            if(v.length!=3 || !v[0].equals(String.format(Locale.ROOT,"customer_%03d",result.size()+1)) || !v[1].matches("[a-f0-9-]{36}") || !v[2].equals("sv3v1-"+v[1]) || !ids.add(v[1])) throw fail("INVALID_MANIFEST");
            result.add(new Customer(v[0],v[1],v[2]));
        }
        if(result.size()!=100) throw fail("MANIFEST_REQUIRES_100_CUSTOMERS");
        // Match the complete ordered seed rows as well, rather than trusting arbitrary manifest IDs.
        String source=Files.readString(root.resolve("seeder.sql"),StandardCharsets.UTF_8);
        int offset=source.indexOf("INSERT INTO `CUSTOMER`");
        int end=source.indexOf(';',offset);
        for(Customer customer:result) {
            int found=source.indexOf("('"+customer.id()+"',NULL,'"+customer.anonymous()+"'",offset);
            if(found<offset || found>=end) throw fail("MANIFEST_SEED_MISMATCH");offset=found+1;
        }
        return List.copyOf(result);
    }

    static List<Target> plan(Connection c,List<Customer> manifest,String adminPassword,String customerPassword,boolean lock) throws Exception {
        var targetIds=new HashSet<String>();var result=new ArrayList<Target>();
        var demoCustomerIds=new HashSet<String>();for(Customer item:manifest) demoCustomerIds.add(item.id());
        // Current/locking reads use stable manifest order; all password work stays inside this operational transaction.
        for(Customer item:manifest) {
            var customer=rows(c,"SELECT user_id,anonymous_code FROM CUSTOMER WHERE customer_id=?"+(lock?" FOR UPDATE":""),item.id());
            if(customer.size()!=1 || !item.anonymous().equals(customer.getFirst().get(1))) throw fail("CUSTOMER_MANIFEST_NOT_PRESENT");
            String linked=customer.getFirst().get(0);
            Account existing=linked==null?account(c,"username",item.username(),lock):account(c,"user_id",linked,lock);
            if(linked!=null && existing==null) throw fail("DANGLING_CUSTOMER_LINK");
            String email=item.username()+"@example.test";
            if(linked==null && existing!=null && (!item.username().equals(existing.username()) || !email.equals(existing.email()))) throw fail("UNLINKED_ACCOUNT_NOT_CONFIRMED_DEMO");
            String id=existing==null?item.id():existing.id();
            for(var link:rows(c,"SELECT customer_id FROM CUSTOMER WHERE user_id=?",id)) if(!link.getFirst().equals(item.id())) throw fail("EXTERNAL_CUSTOMER_LINK_CONFLICT");
            Target target=target(c,id,item.username(),email,"Khách Hàng "+item.username().substring(9),item.id(),existing,Set.of("CUSTOMER"),customerPassword,lock);
            if(!targetIds.add(id)) throw fail("DUPLICATE_ACCOUNT_MAPPING");result.add(target);
        }
        Account admin=account(c,"username","admin.test",lock);
        String id=admin==null?"admin-uuid-001":admin.id();
        if(!targetIds.add(id)) throw fail("ADMIN_CUSTOMER_ID_CONFLICT");
        for(var link:rows(c,"SELECT customer_id FROM CUSTOMER WHERE user_id=?",id)) if(demoCustomerIds.contains(link.getFirst())) throw fail("ADMIN_DEMO_CUSTOMER_CONFLICT");
        result.add(target(c,id,"admin.test","admin@example.test","Hệ Thống Admin",null,admin,Set.of("ADMIN","CUSTOMER"),adminPassword,lock));
        for(String code:List.of("ADMIN","CUSTOMER")) {
            String roleId=scalar(c,"SELECT role_id FROM ROLE WHERE code=?",code);
            if(roleId!=null && !code.equals(scalar(c,"SELECT code FROM ROLE WHERE role_id=?",roleId))) throw fail("ROLE_CODE_CASE_CONFLICT");
        }
        return List.copyOf(result);
    }

    static Target target(Connection c,String id,String username,String email,String name,String customerId,Account existing,Set<String> desiredRoles,String password,boolean lock) throws Exception {
        for(String[] pair:new String[][]{{"user_id",id},{"username",username},{"email",email}}) {
            Account collision=account(c,pair[0],pair[1],lock);
            if(collision!=null && (existing==null || !collision.id().equals(existing.id()))) throw fail("IDENTITY_COLLISION_"+username);
        }
        boolean matches=false;
        if(existing!=null) try { matches=ENCODER.matches(password,existing.hash()); } catch(IllegalArgumentException invalid) { /* Replace unsupported/invalid legacy hash. */ }
        boolean changed=existing==null || !matches || !username.equals(existing.username()) || !email.equals(existing.email())
                || !name.equals(existing.name()) || !"ACTIVE".equals(existing.status()) || !roles(c,id).equals(desiredRoles);
        return new Target(id,username,email,name,customerId,existing,desiredRoles,matches,changed);
    }

    static void verify(Connection c,List<Target> targets,String adminPassword,String customerPassword) throws Exception {
        Set<String> ids=new HashSet<>();
        for(Target t:targets) {
            Account account=account(c,"user_id",t.id(),false);
            if(account==null || !ids.add(account.id()) || !t.username().equals(account.username()) || !t.email().equals(account.email())
                    || !t.name().equals(account.name()) || !"ACTIVE".equals(account.status()) || !roles(c,t.id()).equals(t.roles())
                    || !ENCODER.matches(t.customerId()==null?adminPassword:customerPassword,account.hash())) throw fail("READBACK_MISMATCH");
            if(t.customerId()!=null && !t.id().equals(scalar(c,"SELECT user_id FROM CUSTOMER WHERE customer_id=?",t.customerId()))) throw fail("LINK_READBACK_MISMATCH");
        }
        System.out.println("Readback: VERIFIED accounts=101 passwordMatches=101 exactRoles=101 customerLinks=100 uniqueAccounts=101");
    }

    static Account account(Connection c,String column,String value,boolean lock) throws SQLException {
        if(!Set.of("user_id","username","email").contains(column)) throw fail("INVALID_INTERNAL_COLUMN");
        var values=rows(c,"SELECT user_id,username,email,display_name,status,password_hash FROM USER_ACCOUNT WHERE "+column+"=?"+(lock?" FOR UPDATE":""),value);
        if(values.isEmpty()) return null;var a=values.getFirst();return new Account(a.get(0),a.get(1),a.get(2),a.get(3),a.get(4),a.get(5));
    }
    static Set<String> roles(Connection c,String id) throws SQLException {
        Set<String> roles=new HashSet<>();for(var row:rows(c,"SELECT r.code FROM USER_ROLE ur JOIN ROLE r ON r.role_id=ur.role_id WHERE ur.user_id=?",id))roles.add(row.getFirst());return roles;
    }
    static List<List<String>> rows(Connection c,String sql,Object... args) throws SQLException {
        try(var q=c.prepareStatement(sql)) { for(int i=0;i<args.length;i++)q.setObject(i+1,args[i]);
            try(var rs=q.executeQuery()) {var result=new ArrayList<List<String>>();while(rs.next()) {var row=new ArrayList<String>();for(int i=1;i<=rs.getMetaData().getColumnCount();i++)row.add(rs.getString(i));result.add(row);}return result;}
        }
    }
    static String scalar(Connection c,String sql,Object... args) throws SQLException {var values=rows(c,sql,args);return values.isEmpty()?null:values.getFirst().getFirst();}
    static int update(Connection c,String sql,Object... args) throws SQLException {try(var q=c.prepareStatement(sql)) {for(int i=0;i<args.length;i++)q.setObject(i+1,args[i]);return q.executeUpdate();}}
    static String value(String name,String fallback) {String v=System.getenv(name);if(v==null)v=config.getProperty(name);return v==null?fallback:v;}
    static String required(String name) {String v=value(name,null);if(v==null || v.isBlank())throw fail("MISSING_ENV_"+name);return v;}
    static SafeFailure fail(String code) {return new SafeFailure(code);}
    static class SafeFailure extends RuntimeException {SafeFailure(String message) {super(message);}}
}
