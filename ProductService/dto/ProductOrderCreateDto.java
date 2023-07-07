package com.amrok.product.command.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Date;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductOrderCreateDto {
    private String orderId;
    private Long userId;
    private Long districtId;
    private List<ProductOrderCreateProductDto> products;
    private Long contactAddressId;
    private String wechatTransactionId;
    private String metaData;
    private Date paidAt;
    private String status;
    private Date createdAt;
    private Double totalSellPrice;
    private Double totalOrgPrice;
    private Double totalCoin;
    private Double totalQuantity;
}
