package vn.edu.wspm.backend.testsupport;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.AbstractDataSource;

/** Test-only instrumentation: all connections/statements/results delegate to the real driver. No synthetic rows. */
public final class ObservedDataSource extends AbstractDataSource {
    public interface Observer {
        default void opened(Connection connection) throws SQLException {}
        default void queried(String sql, Connection connection) throws SQLException {}
        default void closedResult(String sql, Connection connection) throws SQLException {}
    }
    private final DataSource delegate;
    private final Observer observer;
    public ObservedDataSource(DataSource delegate, Observer observer) { this.delegate=delegate;this.observer=observer; }

    @Override public Connection getConnection() throws SQLException { return connection(delegate.getConnection()); }
    @Override public Connection getConnection(String user,String password) throws SQLException { return connection(delegate.getConnection(user,password)); }
    private Connection connection(Connection actual) throws SQLException {
        try { observer.opened(actual); }
        catch(SQLException | RuntimeException failure) { actual.close();throw failure; }
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)-> {
            Object value=invoke(actual,method,args);
            if(method.getName().equals("prepareStatement") && value instanceof PreparedStatement statement && args[0] instanceof String sql) {
                return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class<?>[]{PreparedStatement.class},(p,m,a)-> {
                    Object result=invoke(statement,m,a);
                    if(m.getName().equals("executeQuery") && result instanceof ResultSet rows) {
                        try { observer.queried(sql,actual); }
                        catch(SQLException | RuntimeException failure) { rows.close();throw failure; }
                        return Proxy.newProxyInstance(ResultSet.class.getClassLoader(),new Class<?>[]{ResultSet.class},(r,rm,ra)-> {
                            Object next=invoke(rows,rm,ra);
                            if(rm.getName().equals("close")) observer.closedResult(sql,actual);
                            return next;
                        });
                    }
                    return result;
                });
            }
            return value;
        });
    }
    private static Object invoke(Object actual,java.lang.reflect.Method method,Object[] args) throws Throwable {
        try { return method.invoke(actual,args); }
        catch(InvocationTargetException failure) { throw failure.getCause(); }
    }
}
