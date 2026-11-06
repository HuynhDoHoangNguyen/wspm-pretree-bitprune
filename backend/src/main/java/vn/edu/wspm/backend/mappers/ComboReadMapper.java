package vn.edu.wspm.backend.mappers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import vn.edu.wspm.backend.dto.recommendation.ComboReadResponse.*;
import vn.edu.wspm.backend.repositories.recommendation.ComboReadRepository.*;

@Component
public class ComboReadMapper {
    public BatchProvenance batch(BatchRow b) {
        return new BatchProvenance(b.batchId(), b.runId(), b.snapshotId(), b.algorithm(),
                b.rankingPolicy(), b.patternWeightPolicy());
    }

    public Detail detail(ComboRow c, BatchRow b, List<ItemRow> items, Instant at) {
        var mapped = items.stream().sorted(Comparator.comparingLong(ItemRow::itemsetOrder)
                .thenComparingLong(ItemRow::itemOrder)).map(i -> new Item(i.itemsetOrder(),
                        i.itemOrder(), i.quantity(), i.anchorEligible(), i.discountEligible(),
                        money(i.unitPriceSnapshot()), new Product(i.productId(), i.name(), i.sku(),
                                i.categoryId(), i.imageUrl(), money(i.currentPrice())))).toList();
        return new Detail(c.comboId(), c.name(), c.kind(), c.status(), c.explanation(),
                c.validFrom(), c.validTo(), c.publishedAt(), at,
                new Provenance(batch(b), c.primaryPatternId(), c.canonicalPattern()),
                new Metrics(c.supportAbs(), c.supportRatio(), b.sequenceCount(), c.patternWeight()),
                new Pricing("PUBLISH_SNAPSHOT", c.currencyCode(), money(c.originalPrice()),
                        money(c.salePrice()), c.discountType(), money(c.discountValue()),
                        money(c.maxDiscountAmount()), false), mapped);
    }

    private static String money(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
