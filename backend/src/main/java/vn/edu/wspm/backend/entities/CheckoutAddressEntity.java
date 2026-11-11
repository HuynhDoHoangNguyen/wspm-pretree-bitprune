package vn.edu.wspm.backend.entities;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "CHECKOUT_ADDRESS")
public class CheckoutAddressEntity {
    @Id
    @Size(max = 40)
    @Column(name = "checkout_address_id", nullable = false, length = 40)
    private String checkoutAddressId;

    @NotNull
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "checkout_id", nullable = false)
    private CheckoutSessionEntity checkout;

    @Size(max = 16)
    @NotNull
    @Column(name = "address_type", nullable = false, length = 16)
    private String addressType;

    @Size(max = 160)
    @NotNull
    @Column(name = "recipient_name", nullable = false, length = 160)
    private String recipientName;

    @Size(max = 32)
    @NotNull
    @Column(name = "phone", nullable = false, length = 32)
    private String phone;

    @Size(max = 500)
    @NotNull
    @Column(name = "address_line", nullable = false, length = 500)
    private String addressLine;

    @Size(max = 32)
    @Column(name = "ward_code", length = 32)
    private String wardCode;

    @Size(max = 160)
    @NotNull
    @Column(name = "ward_name", nullable = false, length = 160)
    private String wardName;

    @Size(max = 32)
    @Column(name = "district_code", length = 32)
    private String districtCode;

    @Size(max = 160)
    @NotNull
    @Column(name = "district_name", nullable = false, length = 160)
    private String districtName;

    @Size(max = 32)
    @Column(name = "province_code", length = 32)
    private String provinceCode;

    @Size(max = 160)
    @NotNull
    @Column(name = "province_name", nullable = false, length = 160)
    private String provinceName;

    @Size(max = 20)
    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Size(max = 2)
    @NotNull
    @ColumnDefault("'VN'")
    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    public String getCheckoutAddressId() {
        return checkoutAddressId;
    }

    public void setCheckoutAddressId(String checkoutAddressId) {
        this.checkoutAddressId = checkoutAddressId;
    }

    public CheckoutSessionEntity getCheckout() {
        return checkout;
    }

    public void setCheckout(CheckoutSessionEntity checkout) {
        this.checkout = checkout;
    }

    public String getAddressType() {
        return addressType;
    }

    public void setAddressType(String addressType) {
        this.addressType = addressType;
    }

    public String getRecipientName() {
        return recipientName;
    }

    public void setRecipientName(String recipientName) {
        this.recipientName = recipientName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getAddressLine() {
        return addressLine;
    }

    public void setAddressLine(String addressLine) {
        this.addressLine = addressLine;
    }

    public String getWardCode() {
        return wardCode;
    }

    public void setWardCode(String wardCode) {
        this.wardCode = wardCode;
    }

    public String getWardName() {
        return wardName;
    }

    public void setWardName(String wardName) {
        this.wardName = wardName;
    }

    public String getDistrictCode() {
        return districtCode;
    }

    public void setDistrictCode(String districtCode) {
        this.districtCode = districtCode;
    }

    public String getDistrictName() {
        return districtName;
    }

    public void setDistrictName(String districtName) {
        this.districtName = districtName;
    }

    public String getProvinceCode() {
        return provinceCode;
    }

    public void setProvinceCode(String provinceCode) {
        this.provinceCode = provinceCode;
    }

    public String getProvinceName() {
        return provinceName;
    }

    public void setProvinceName(String provinceName) {
        this.provinceName = provinceName;
    }

    public String getPostalCode() {
        return postalCode;
    }

    public void setPostalCode(String postalCode) {
        this.postalCode = postalCode;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public void setCountryCode(String countryCode) {
        this.countryCode = countryCode;
    }

}
