package vn.edu.wspm.backend.services.dataset.csv;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import vn.edu.wspm.backend.repositories.ProductRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class RepositoryProductReferenceLookupTest {
    private final ProductRepository repository = mock(ProductRepository.class);
    private final RepositoryProductReferenceLookup lookup = new RepositoryProductReferenceLookup(repository);

    @Test
    void emptyInputDoesNotQueryCatalog() {
        assertTrue(lookup.findExisting(Set.of()).isEmpty());
        verifyNoInteractions(repository);
    }

    @Test
    void filtersCollationEquivalentCandidatesAndReturnsImmutableExactIds() {
        when(repository.findReferenceCandidates(anyCollection()))
                .thenReturn(List.of("Product-A", "café", "product-space ", "exact", "unexpected"));
        var result = lookup.findExisting(Set.of("product-a", "cafe", "product-space", "exact"));
        assertEquals(Set.of("exact"), result);
        assertThrows(UnsupportedOperationException.class, () -> result.add("other"));
        verify(repository).findReferenceCandidates(List.of("cafe", "exact", "product-a", "product-space"));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void batchesIdOnlyQueriesWithoutReadingCatalogPerRow() {
        var requested = new HashSet<String>();
        for (int index = 0; index < 600; index++) requested.add("product-" + index);
        var batches = new ArrayList<List<String>>();
        when(repository.findReferenceCandidates(anyCollection())).thenAnswer(call -> {
            Collection<String> batch = call.getArgument(0);
            batches.add(List.copyOf(batch));
            return List.copyOf(batch);
        });
        var existing = lookup.findExisting(requested);
        assertEquals(requested, existing);
        assertEquals(List.of(256, 256, 88), batches.stream().map(List::size).toList());
        assertEquals(requested.stream().sorted().toList(), new ArrayList<>(existing));
        assertEquals(requested.stream().sorted().toList(), batches.stream().flatMap(List::stream).toList());
        verify(repository, times(3)).findReferenceCandidates(anyCollection());
        verifyNoMoreInteractions(repository);
    }

    @Test
    void databaseFailurePropagatesAndNeverBecomesUnknownProduct() {
        var failure = new DataAccessResourceFailureException("synthetic");
        when(repository.findReferenceCandidates(anyCollection())).thenThrow(failure);
        assertSame(failure, assertThrows(DataAccessResourceFailureException.class,
                () -> lookup.findExisting(Set.of("product"))));
    }
}
