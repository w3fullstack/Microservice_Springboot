package com.amrok.product.command.dto;

import com.amrok.product.command.models.ProductStatus;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.util.List;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductCreateDto {
    @NotNull
    private String name;
    @NotNull
    private Long kindId;
    private String description;
    private ProductStatus status;
    private Integer orderNo;
    private JsonNode coverMedia;
    private JsonNode detailMedia;
    private JsonNode meta;
    private List<ProductSpecCreateDto> specs;
}
