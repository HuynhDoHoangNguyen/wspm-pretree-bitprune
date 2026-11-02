package vn.edu.wspm.backend.dto.combo;

public record ComboItemResponse(
        String comboId,
        Integer itemsetOrder,
        Integer itemOrder,
        String productId
) {
}

