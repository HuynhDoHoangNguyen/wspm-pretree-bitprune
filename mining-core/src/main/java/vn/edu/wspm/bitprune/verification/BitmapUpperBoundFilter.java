package vn.edu.wspm.bitprune.verification;

import java.util.Objects;
import vn.edu.wspm.bitprune.bitmap.SidBitmap;
import vn.edu.wspm.config.ResolvedMinimumSupport;

/**
 * Stateless upper-bound pruning filter dựa trên bitmap cardinality của
 * candidate.
 *
 * <h2>Safety claim</h2>
 *
 * <p>
 * Filter này an toàn <em>tương đối với frozen baseline Java {@code double}
 * acceptance
 * predicate</em> ({@link ResolvedMinimumSupport#meetsThreshold}). Nó KHÔNG được
 * tuyên bố
 * là exact-real safe.
 *
 * <p>
 * Policy được Research Reviewer duyệt:
 * {@code BASELINE_RELATIVE_MONOTONE_UPPER_BOUND}.
 *
 * <h2>Bitmap cardinality là cận trên cần thiết (necessary upper bound)</h2>
 *
 * <p>
 * Theo C01/C02 architecture contract:
 * 
 * <pre>
 *   ActualSupportingSIDs(P) ⊆ CandidateBitmapSIDs(P)
 *   support(P) ≤ candidateBitmap.cardinality()
 * </pre>
 *
 * <p>
 * Bitmap cardinality {@code c} là cận trên của exact support, không phải exact
 * support.
 * Các nguyên nhân làm cận lỏng bao gồm temporal false positives (thứ tự ngược
 * hoặc
 * same-itemset mismatch) và repeated-item bitmap identity (BM(a) AND BM(a) ==
 * BM(a)).
 * Filter không tuyên bố đây là exact temporal support và không thực hiện exact
 * temporal
 * verification.
 *
 * <h2>Monotonicity proof cho safety</h2>
 *
 * <p>
 * Với {@code 0 ≤ exactSupport ≤ c} và {@code meanWeight ≥ 0} finite, theo Java
 * 21
 * JLS §15.4/§15.17.1, phép nhân finite được rounded theo
 * {@code roundTiesToEven}; ánh
 * xạ làm tròn nearest là đơn điệu, do đó:
 * 
 * <pre>
 *   rounded(exactSupport × meanWeight) ≤ rounded(c × meanWeight)
 * </pre>
 *
 * <p>
 * Nếu filter prune (tức là frozen baseline reject tại {@code c}), thì frozen
 * baseline
 * cũng reject mọi exact support khả dĩ trong {@code [0, c]}. Không có false
 * negative
 * tương đối với baseline.
 *
 * <h2>Exact-real counterexample — không generalize safety</h2>
 *
 * <p>
 * Với {@code sequenceCount=25}, {@code percentage=14.0}, {@code cardinality=7},
 * {@code meanWeight=0.5}:
 * <ul>
 * <li>Exact-real: {@code 7 × 1/2 = 3.5 == 14/100 × 25 = 3.5} → equality, không
 * được prune.</li>
 * <li>Java double: {@code (14.0/100.0)*25 = 3.5000000000000004},
 * {@code 7*0.5 = 3.5}
 * → {@code 3.5 < 3.5000000000000004} → baseline reject → filter returns
 * {@code true}.</li>
 * </ul>
 *
 * <p>
 * Đây là false negative tương đối với exact-real semantics nhưng không phải
 * false
 * negative tương đối với frozen baseline. Policy
 * {@code BASELINE_RELATIVE_MONOTONE_UPPER_BOUND}
 * không được trình bày như exact-real correctness.
 *
 * <h2>No-prune tại equality</h2>
 *
 * <p>
 * Nếu rounded product {@code cardinality * meanWeight} bằng
 * {@code effectiveThreshold}
 * theo IEEE 754 binary64, {@code meetsThreshold} trả {@code true} và filter trả
 * {@code false} (không prune).
 *
 * <h2>No-op tại threshold 0</h2>
 *
 * <p>
 * Nếu {@code effectiveThreshold == 0.0} (bao gồm {@code +0.0} và {@code -0.0}),
 * filter trả {@code false} mà không gọi {@code meetsThreshold}. C06 không giải
 * quyết
 * R6 enumeration semantics; R6 vẫn OPEN.
 *
 * <h2>Stateless</h2>
 *
 * <p>
 * Class này không có state. Mọi quyết định đều dựa trên tham số đầu vào và
 * delegate
 * tới frozen predicate. Không phụ thuộc PRISM, Prefix Tree, CMAP, hay temporal
 * scanner.
 */
public final class BitmapUpperBoundFilter {

    /**
     * Kiểm tra xem candidate có thể bị prune bằng bitmap cardinality upper-bound
     * hay không.
     *
     * <p>
     * Validation order được yêu cầu:
     * <ol>
     * <li>Reject null {@code candidateBitmap}.</li>
     * <li>Reject null {@code resolvedMinimumSupport}.</li>
     * <li>Yêu cầu
     * {@code candidateBitmap.sequenceCount() == resolvedMinimumSupport.numberOfSequences()}.
     * Nếu khác, ném {@link IllegalArgumentException}.</li>
     * <li>Yêu cầu {@code globalMeanWeight} finite và không âm (về mặt số học;
     * {@code -0.0} được coi là zero, không phải negative error).</li>
     * <li>Nếu {@code effectiveThreshold} bằng zero về mặt số học, trả
     * {@code false}.</li>
     * <li>Delegate tới frozen predicate: trả
     * {@code !resolvedMinimumSupport.meetsThreshold(
     *       candidateBitmap.cardinality(), globalMeanWeight)}.</li>
     * </ol>
     *
     * <p>
     * <strong>Overflow:</strong> Nếu phép nhân
     * {@code cardinality * globalMeanWeight} tạo
     * ra giá trị không finite (overflow thành infinity), {@code meetsThreshold} ném
     * {@link IllegalArgumentException}. Exception này được propagate; không được
     * chuyển
     * thành quyết định prune.
     *
     * <p>
     * <strong>Comparator:</strong> Quyết định chỉ delegate tới
     * {@code meetsThreshold}.
     * Không có comparator semantics thứ hai bằng phép nhân thủ công. Không có
     * epsilon.
     * Không có {@code Math.ceil}, {@code floor}, hay {@code round}.
     *
     * @param candidateBitmap        bitmap candidate (upper bound, không phải exact
     *                               support)
     * @param globalMeanWeight       MeanW global của database, phải finite và
     *                               {@code >= 0}
     * @param resolvedMinimumSupport ngưỡng đã resolve, phải có cùng universe size
     *                               với bitmap
     * @return {@code true} nếu và chỉ nếu frozen baseline reject mọi exact support
     *         khả dĩ trong {@code [0, cardinality]}; {@code false} nếu candidate
     *         có thể được chấp nhận hoặc phải chờ exact temporal verification
     * @throws NullPointerException     nếu {@code candidateBitmap} hoặc
     *                                  {@code resolvedMinimumSupport} là null
     * @throws IllegalArgumentException nếu universe size không khớp,
     *                                  {@code globalMeanWeight}
     *                                  không finite hoặc âm hữu hạn, hoặc nếu phép
     *                                  nhân
     *                                  nội bộ tạo kết quả không finite (overflow)
     */
    public boolean canPrune(
            SidBitmap candidateBitmap,
            double globalMeanWeight,
            ResolvedMinimumSupport resolvedMinimumSupport) {

        // Bước 1: Reject null candidateBitmap
        Objects.requireNonNull(candidateBitmap, "candidateBitmap must not be null");

        // Bước 2: Reject null resolvedMinimumSupport
        Objects.requireNonNull(resolvedMinimumSupport, "resolvedMinimumSupport must not be null");

        // Bước 3: Xác minh SID universe size nhất quán
        int bitmapSize = candidateBitmap.sequenceCount();
        int resolvedSize = resolvedMinimumSupport.numberOfSequences();
        if (bitmapSize != resolvedSize) {
            throw new IllegalArgumentException(
                    "SID universe size mismatch: candidateBitmap.sequenceCount()="
                            + bitmapSize
                            + " but resolvedMinimumSupport.numberOfSequences()="
                            + resolvedSize);
        }

        // Bước 4: Yêu cầu globalMeanWeight finite và không âm về mặt số học.
        // -0.0 == 0.0 là true theo IEEE 754 comparison, nên không bị từ chối ở đây.
        // Kiểm tra !isFinite || < 0.0 sẽ reject NaN, ±Infinity, và âm hữu hạn.
        // -0.0 thỏa isFinite và -0.0 < 0.0 là false theo IEEE 754, nên được coi là
        // zero.
        if (!Double.isFinite(globalMeanWeight) || globalMeanWeight < 0.0) {
            throw new IllegalArgumentException(
                    "globalMeanWeight must be finite and non-negative; got: " + globalMeanWeight);
        }

        // Bước 5: Nếu effectiveThreshold bằng zero về mặt số học, no-op pruning.
        // Dùng comparison số học, không raw sign bit. +0.0 == 0.0 và -0.0 == 0.0 đều
        // đúng.
        // Điều này không giải quyết R6 enumeration semantics; R6 vẫn OPEN.
        double effectiveThreshold = resolvedMinimumSupport.effectiveThreshold();
        if (effectiveThreshold == 0.0) {
            return false;
        }

        // Bước 6: Delegate hoàn toàn tới frozen baseline predicate.
        // Không duplicate bằng: candidateBitmap.cardinality() * globalMeanWeight <
        // effectiveThreshold
        // Lý do: tránh tạo comparator semantics thứ hai; bit-for-bit parity với frozen
        // baseline.
        // Không bắt IllegalArgumentException từ meetsThreshold rồi chuyển thành prune.
        // Nếu multiplication overflow tạo nonfinite result, meetsThreshold ném
        // IllegalArgumentException và exception được propagate.
        return !resolvedMinimumSupport.meetsThreshold(
                candidateBitmap.cardinality(),
                globalMeanWeight);
    }
}
