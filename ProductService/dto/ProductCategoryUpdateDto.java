package com.amrok.product.command.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductCategoryUpdateDto {
    private String name;
    private Long districtId;
    private String imageUrl;
    private Integer orderNo;
}
