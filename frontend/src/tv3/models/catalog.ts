/** P1 read DTOs. IDs remain strings; nullable Java fields are not given invented defaults. */
export interface CategoryResponse {
  readonly categoryId: string;
  readonly categoryName: string | null;
}

export interface ProductResponse {
  readonly productId: string;
  readonly productName: string | null;
  readonly categoryId: string | null;
  readonly productStatus: string | null;
  /** JSON number from BigDecimal; display only, not a client-side money calculation. */
  readonly price: number | null;
  readonly imageUrl: string | null;
  /** LocalDateTime as received: do not infer a timezone or change the server order. */
  readonly createdAt: string | null;
}

export interface ProductImageResponse {
  readonly imageId: string;
  readonly imageUrl: string;
  readonly altText: string | null;
  readonly displayOrder: number;
}

export interface ProductDetailResponse extends ProductResponse {
  readonly images: readonly ProductImageResponse[];
}
