package vn.edu.wspm.backend.repositories.recommendation;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.edu.wspm.backend.testsupport.OwnedMySql;
import static org.junit.jupiter.api.Assertions.*;

/** Actual MySQL reader query: active warehouse aggregation, reservations, zero and unknown stock. */
class ComboStockMySqlTest {
    @Test void readsExactAvailableStockInOneBatchWithoutChangingInventory() throws Exception {
        try(var mysql=new OwnedMySql()) {
            var jdbc=new JdbcTemplate(mysql.dataSource());
            jdbc.execute("CREATE TABLE PRODUCT(product_id VARCHAR(40) PRIMARY KEY,name VARCHAR(100),sku VARCHAR(80),category_id VARCHAR(40),current_price DECIMAL(19,2))");
            jdbc.execute("CREATE TABLE PRODUCT_IMAGE(image_id VARCHAR(40),product_id VARCHAR(40),image_url VARCHAR(100),display_order INT)");
            jdbc.execute("CREATE TABLE COMBO_ITEM(combo_id VARCHAR(40),itemset_order INT,item_order INT,quantity INT,is_anchor_eligible BOOLEAN,is_discount_eligible BOOLEAN,unit_price_snapshot DECIMAL(19,2),product_id VARCHAR(40))");
            jdbc.execute("CREATE TABLE WAREHOUSE(warehouse_id VARCHAR(40) PRIMARY KEY,status VARCHAR(24))");
            jdbc.execute("CREATE TABLE INVENTORY_BALANCE(warehouse_id VARCHAR(40),product_id VARCHAR(40),on_hand_quantity INT UNSIGNED,reserved_quantity INT UNSIGNED)");
            jdbc.update("INSERT INTO WAREHOUSE VALUES('a','ACTIVE'),('b','ACTIVE'),('off','INACTIVE')");
            int order=0;
            for(String id:List.of("available","zero","missing","inactive-only","large")) {
                jdbc.update("INSERT INTO PRODUCT VALUES(?,?,?,'cat',10)",id,id,id);
                jdbc.update("INSERT INTO COMBO_ITEM VALUES('combo',1,?,1,TRUE,FALSE,10,?)",++order,id);
            }
            jdbc.update("INSERT INTO INVENTORY_BALANCE VALUES('a','available',10,3),('b','available',5,2),('off','available',100,0),('a','zero',3,3),('off','inactive-only',99,0),('a','large',4294967295,0),('b','large',4294967295,0)");
            var before=jdbc.queryForList("SELECT * FROM INVENTORY_BALANCE ORDER BY warehouse_id,product_id");
            var rows=new ComboReadRepository(jdbc).items(List.of("combo"));
            var byProduct=rows.stream().collect(Collectors.toMap(ComboReadRepository.ItemRow::productId,Function.identity()));
            assertEquals(5,rows.size());
            assertEquals("10",byProduct.get("available").availableQuantity());
            assertEquals("0",byProduct.get("zero").availableQuantity());
            assertNull(byProduct.get("missing").availableQuantity());
            assertNull(byProduct.get("inactive-only").availableQuantity());
            assertEquals("8589934590",byProduct.get("large").availableQuantity());
            assertEquals(before,jdbc.queryForList("SELECT * FROM INVENTORY_BALANCE ORDER BY warehouse_id,product_id"));
        }
    }
}
