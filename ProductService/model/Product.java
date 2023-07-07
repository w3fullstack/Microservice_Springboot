package com.amrok.product.command.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.TypeDef;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "product")
@EntityListeners(AuditingEntityListener.class)
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@TypeDef(
        typeClass = JsonBinaryType.class,
        defaultForType = JsonNode.class
)
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @ApiModelProperty(value = "id")
    private Long id;

    @NotNull
    private String name;

    @ManyToOne(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "kindId", insertable = true, updatable = false)
    private ProductKind kind;

    @Column(length = 5000)
    private String description;

    private ProductStatus status;

    private Integer orderNo;

    private Integer soldCount;

    @Column(columnDefinition = "jsonb")
    private JsonNode coverMedia;

    @Column(columnDefinition = "jsonb")
    private JsonNode detailMedia;

    @Column(columnDefinition = "jsonb")
    private JsonNode meta;

    @JsonIgnoreProperties("product")
    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @JoinColumn(name = "productId")
    private List<ProductSpec> specs;

    @CreatedDate
    @Column(name = "createdAt", updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "modifiedAt")
    private Instant updatedAt;

    private Long createdBy;
    private Long updatedBy;
}
