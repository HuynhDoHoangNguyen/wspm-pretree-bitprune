package vn.edu.wspm.backend.services.dataset.conversion;

import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DuplicateKeyException;
import vn.edu.wspm.backend.exception.ApplicationException;
import vn.edu.wspm.backend.exception.ErrorCode;
import vn.edu.wspm.backend.repositories.dataset.ProductItemMappingStore;
import vn.edu.wspm.backend.services.dataset.csv.ParsedCsvRecord;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductItemMappingServiceTest {
    static VerifiedConversionInput proof(String... products) {
        var records = new ArrayList<ParsedCsvRecord>();
        for (String product : products) {
            var fields = new ArrayList<>(java.util.Collections.nCopies(13, "unused"));
            fields.set(5, product);
            long number = records.size() + 2L;
            records.add(new ParsedCsvRecord(number, number, number, fields));
        }
        // Package-local fixture: production callers receive this proof exclusively from the gate.
        return new VerifiedConversionInput(new byte[0], null, records);
    }

    @Test void exactDistinctProductsAreSortedWithoutNormalization() {
        var store = mock(ProductItemMappingStore.class);
        var result = new ProductItemDictionary(Map.of(" A", 9, "A", 2, "a", 7, "é", 11, "e\u0301", 12));
        when(store.resolve(List.of(" A", "A", "a", "e\u0301", "é"))).thenReturn(result);
        assertSame(result, new ProductItemMappingService(store).resolve(proof("é", "a", "A", " A", "a", "e\u0301")));
        verify(store).resolve(List.of(" A", "A", "a", "e\u0301", "é"));
    }

    @Test void retriesOnlyTransactionContention() {
        var store = mock(ProductItemMappingStore.class);
        var result = new ProductItemDictionary(Map.of("p", 1));
        when(store.resolve(List.of("p"))).thenThrow(new DuplicateKeyException("private"))
                .thenThrow(new CannotAcquireLockException("private")).thenReturn(result);
        assertSame(result, new ProductItemMappingService(store).resolve(proof("p")));
        verify(store, times(3)).resolve(List.of("p"));
    }

    @Test void retriesAreBoundedAndReturnSafeFailure() {
        var store = mock(ProductItemMappingStore.class);
        when(store.resolve(anyList())).thenThrow(new DuplicateKeyException("private product"));
        var failure = assertThrows(ApplicationException.class,
                () -> new ProductItemMappingService(store).resolve(proof("p")));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, failure.errorCode());
        assertFalse(failure.getMessage().contains("private"));
        verify(store, times(4)).resolve(List.of("p"));
    }

    @Test void unavailableDatabaseIsNotRetried() {
        var store = mock(ProductItemMappingStore.class);
        when(store.resolve(anyList())).thenThrow(new DataAccessResourceFailureException("private connection"));
        var failure = assertThrows(ApplicationException.class,
                () -> new ProductItemMappingService(store).resolve(proof("p")));
        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, failure.errorCode());
        assertNull(failure.getCause());
        verify(store).resolve(List.of("p"));
    }

    @Test void incompatibleStateIsNotRetried() {
        var store = mock(ProductItemMappingStore.class);
        var invalid = new ApplicationException(ErrorCode.INVALID_RESOURCE_STATE);
        when(store.resolve(anyList())).thenThrow(invalid);
        assertSame(invalid, assertThrows(ApplicationException.class,
                () -> new ProductItemMappingService(store).resolve(proof("p"))));
        verify(store).resolve(List.of("p"));
    }

    @Test void emptyKeptSetHasNoDatabaseEffects() {
        var store = mock(ProductItemMappingStore.class);
        assertTrue(new ProductItemMappingService(store).resolve(proof()).productToItem().isEmpty());
        verifyNoInteractions(store);
    }

    @Test void invalidInternalInputFailsBeforePersistence() {
        var store = mock(ProductItemMappingStore.class);
        var service = new ProductItemMappingService(store);
        assertThrows(NullPointerException.class, () -> service.resolve(null));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(proof(" ")));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(proof("x".repeat(41))));
        var malformed = new VerifiedConversionInput(new byte[0], null,
                List.of(new ParsedCsvRecord(2, 2, 2, List.of("p"))));
        assertThrows(IllegalArgumentException.class, () -> service.resolve(malformed));
        verifyNoInteractions(store);
    }

    @Test void dictionaryIsBijectiveOrderedAndDefensive() {
        var source = new LinkedHashMap<>(Map.of("p2", 27, "p1", 12));
        var result = new ProductItemDictionary(source);
        source.clear();
        assertEquals(List.of("p1", "p2"), List.copyOf(result.productToItem().keySet()));
        assertEquals(List.of(12, 27), List.copyOf(result.itemToProduct().keySet()));
        result.productToItem().forEach((p, i) -> assertEquals(p, result.itemToProduct().get(i)));
        assertThrows(UnsupportedOperationException.class, () -> result.productToItem().put("x", 3));
        assertThrows(UnsupportedOperationException.class, () -> result.itemToProduct().clear());
        assertFalse(result.toString().contains("p1"));
        assertEquals(Integer.MAX_VALUE, new ProductItemDictionary(Map.of("p", Integer.MAX_VALUE)).productToItem().get("p"));
    }

    @Test void dictionaryRejectsCollisionAndInvalidIds() {
        assertThrows(IllegalArgumentException.class, () -> new ProductItemDictionary(Map.of("p", 1, "q", 1)));
        assertThrows(IllegalArgumentException.class, () -> new ProductItemDictionary(Map.of("p", 0)));
        assertThrows(IllegalArgumentException.class, () -> new ProductItemDictionary(Map.of("p", -1)));
        assertThrows(IllegalArgumentException.class, () -> new ProductItemDictionary(Map.of(" ", 1)));
        var nullValue = new LinkedHashMap<String, Integer>(); nullValue.put("p", null);
        assertThrows(IllegalArgumentException.class, () -> new ProductItemDictionary(nullValue));
    }

    @Test void dictionaryIdsAreAcceptedByUnchangedCoreParser() throws Exception {
        var dictionary = new ProductItemDictionary(Map.of("p", 1, "q", Integer.MAX_VALUE));
        var tokens = dictionary.itemToProduct().keySet().stream().map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(" ")) + " -1 -2";
        var database = new vn.edu.wspm.io.SpmfSequenceParser().parse(new java.io.StringReader(tokens));
        assertEquals(1, database.size());
        var itemset = database.records().getFirst().sequence().itemsets().getFirst();
        assertEquals(List.of(1, Integer.MAX_VALUE), itemset.items().stream().map(vn.edu.wspm.model.Item::id).toList());
    }
}
